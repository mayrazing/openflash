package openflash_plugin.tts.service.impl;

import jakarta.annotation.PreDestroy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_plugin.tts.common.TtsErrorCode;
import openflash_plugin.tts.config.TtsProperties;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.service.TtsDeckSettingsService;
import openflash_plugin.tts.service.TtsService;
import tools.jackson.databind.ObjectMapper;

@Service
public class TtsServiceImpl implements TtsService {

    private static final int MAX_TEXT_LENGTH = 500;

    private final TtsProperties ttsProperties;
    private final TtsFeatureGuard featureGuard;
    private final TtsDeckSettingsService ttsDeckSettingsService;
    private final ObjectMapper objectMapper;
    private final Semaphore requestSemaphore;
    private final ThreadPoolExecutor synthesisExecutor;
    private final TtsRequestAdmissionGuard requestAdmissionGuard;
    private final ConcurrentHashMap<String, FutureTask<byte[]>> inFlightRequests;

    @Autowired
    public TtsServiceImpl(
            TtsProperties ttsProperties,
            TtsFeatureGuard featureGuard,
            TtsDeckSettingsService ttsDeckSettingsService,
            ObjectMapper objectMapper) {
        this.ttsProperties = ttsProperties;
        this.featureGuard = featureGuard;
        this.ttsDeckSettingsService = ttsDeckSettingsService;
        this.objectMapper = objectMapper;
        int maxConcurrentRequests = ttsProperties.getMaxConcurrentRequests();
        int requestQueueCapacity = ttsProperties.getRequestQueueCapacity();
        this.requestSemaphore = new Semaphore(maxConcurrentRequests, true);
        this.synthesisExecutor = createSynthesisExecutor(maxConcurrentRequests, requestQueueCapacity);
        this.requestAdmissionGuard = new TtsRequestAdmissionGuard(
                Math.addExact(maxConcurrentRequests, requestQueueCapacity),
                ttsProperties.getMaxConcurrentRequestsPerUser());
        this.inFlightRequests = new ConcurrentHashMap<>();
        validateLoopbackServiceUrl(ttsProperties.getServiceUrl());
        validateLoopbackServiceUrl(ttsProperties.getPiperServiceUrl());
    }

    static String normalizeSpeechText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim()
                .replace("/", " or ")
                .replaceAll("(?i)\\bsb\\.", "somebody")
                .replaceAll("(?i)\\bsp\\.", "someplace")
                .replaceAll("(?i)\\bsth\\.", "something");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String ensureTerminalPunctuation(String text) {
        return text.matches(".*\\p{P}$") ? text : text + ".";
    }

    static boolean isEnglish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hasAsciiLetter = false;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z') {
                hasAsciiLetter = true;
                continue;
            }
            if (Character.isLetter(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK) {
                return false;
            }
        }
        return hasAsciiLetter;
    }

    @Override
    public TtsVariantRequest createVariantRequest(String text) {
        return createVariantRequest(text, TtsFeatureGuard.ENGINE_COSYVOICE3);
    }

    TtsVariantRequest createVariantRequest(String text, String engine) {
        String normalized = normalizeSpeechText(text);
        if (normalized == null) {
            return null;
        }
        String selectedEngine = "piper".equals(engine) ? "piper" : "cosyvoice3";
        String upstreamText = "piper".equals(selectedEngine)
            ? normalized
            : ensureTerminalPunctuation(normalized);
        if (upstreamText.length() > MAX_TEXT_LENGTH) {
            throw new AppException(TtsErrorCode.TTS_TEXT_TOO_LONG);
        }
        if (!isEnglish(normalized)) {
            throw new AppException(TtsErrorCode.TTS_ENGLISH_ONLY);
        }
        String voice = ttsProperties.getVoice();
        double speed = "piper".equals(selectedEngine)
            ? ttsProperties.getPiperSpeed()
            : ttsProperties.getSpeed();
        String accent = ttsProperties.getAccent();
        String engineVersion = "piper".equals(selectedEngine)
            ? ttsProperties.getPiperEngineVersion()
            : ttsProperties.getEngineVersion();
        return new TtsVariantRequest(
                normalized,
                voice,
                speed,
                accent,
                selectedEngine,
                engineVersion,
                buildInFlightKey(normalized, voice, speed, selectedEngine, engineVersion));
    }

    @Override
    public byte[] getAudioBytes(String text) {
        return getAudioBytes(null, (Long) null, text);
    }

    @Override
    public byte[] getAudioBytes(Long ownerUserId, String text) {
        return getAudioBytes(ownerUserId, (Long) null, text);
    }

    @Override
    public byte[] getAudioBytes(Long ownerUserId, Long deckId, String text) {
        featureGuard.ensureTtsEnabled();
        String engine = TtsFeatureGuard.ENGINE_COSYVOICE3;
        if (deckId != null) {
            TtsDeckSettings settings = ttsDeckSettingsService.getForCurrentUser(deckId);
            engine = settings.engine();
        }
        return synthesizeForEngine(ownerUserId, text, engine);
    }

    @Override
    public byte[] getAudioBytes(Long ownerUserId, String text, String engine) {
        featureGuard.ensureTtsEnabled();
        return synthesizeForEngine(ownerUserId, text, engine);
    }

    private byte[] synthesizeForEngine(Long ownerUserId, String text, String engine) {
        TtsVariantRequest request = createVariantRequest(text, engine);
        if (request == null) {
            throw new AppException(TtsErrorCode.TTS_TEXT_BLANK);
        }
        return runInteractive(ownerUserId, () -> waitForInFlightRequest(request));
    }

    /**
     * 同一合成参数只触发一个瞬时上游请求. 请求结束后立即移除, 不形成服务端缓存.
     */
    private byte[] waitForInFlightRequest(TtsVariantRequest request) {
        FutureTask<byte[]> newTask = new FutureTask<>(() -> synthesizeAudio(request));
        String inFlightKey = request.inFlightKey();
        FutureTask<byte[]> runningTask = inFlightRequests.putIfAbsent(inFlightKey, newTask);
        if (runningTask == null) {
            runningTask = newTask;
            try {
                FutureTask<byte[]> scheduledTask = runningTask;
                synthesisExecutor.execute(() -> {
                    try {
                        scheduledTask.run();
                    } finally {
                        inFlightRequests.remove(inFlightKey, scheduledTask);
                    }
                });
            } catch (RejectedExecutionException ex) {
                runningTask.cancel(false);
                inFlightRequests.remove(inFlightKey, runningTask);
                throw new AppException(TtsErrorCode.TTS_BUSY);
            }
        }
        try {
            return waitForTask(runningTask);
        } finally {
            if (runningTask.isDone()) {
                inFlightRequests.remove(inFlightKey, runningTask);
            }
        }
    }

    private byte[] synthesizeAudio(TtsVariantRequest request) {
        try {
            UpstreamResponse response = requestAudioWithPermit(request);
            if (!isValidWavResponse(response)) {
                throw new AppException(TtsErrorCode.TTS_UPSTREAM_ERROR);
            }
            return response.body();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(TtsErrorCode.TTS_UPSTREAM_ERROR);
        }
    }

    private <T> T runInteractive(Long ownerUserId, Callable<T> action) {
        try {
            return requestAdmissionGuard.call(ownerUserId, action);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(TtsErrorCode.TTS_UPSTREAM_ERROR);
        }
    }

    private byte[] waitForTask(FutureTask<byte[]> task) {
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(TtsErrorCode.TTS_UPSTREAM_ERROR);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AppException(TtsErrorCode.TTS_UPSTREAM_ERROR);
        } catch (java.util.concurrent.CancellationException ex) {
            throw new AppException(TtsErrorCode.TTS_BUSY);
        }
    }

    @PreDestroy
    void shutdownSynthesisExecutor() {
        synthesisExecutor.shutdownNow();
    }

    private ThreadPoolExecutor createSynthesisExecutor(int concurrency, int queueCapacity) {
        AtomicInteger threadSequence = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "tts-synthesis-" + threadSequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private UpstreamResponse requestAudioWithPermit(TtsVariantRequest request)
            throws java.io.IOException, InterruptedException {
        boolean acquired = requestSemaphore.tryAcquire(
                ttsProperties.getCapacityWaitTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new AppException(TtsErrorCode.TTS_BUSY);
        }
        try {
            return requestAudio(request);
        } finally {
            requestSemaphore.release();
        }
    }

    /**
     * 使用 HTTP/1.1 JSON 发送方式调用本地 TTS 服务.
     */
    private UpstreamResponse requestAudio(TtsVariantRequest request) throws java.io.IOException {
        byte[] payload = buildRequestBody(request).getBytes(StandardCharsets.UTF_8);
        String serviceUrl = "piper".equals(request.engine())
            ? ttsProperties.getPiperServiceUrl()
            : ttsProperties.getServiceUrl();
        HttpURLConnection connection = (HttpURLConnection) URI.create(serviceUrl).toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(Math.toIntExact(ttsProperties.getConnectTimeoutMillis()));
        connection.setReadTimeout(Math.toIntExact(ttsProperties.getRequestTimeoutMillis()));
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "audio/wav");
        connection.setRequestProperty("Connection", "close");
        connection.setFixedLengthStreamingMode(payload.length);
        try {
            connection.connect();
            try (java.io.OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }
            int statusCode = connection.getResponseCode();
            byte[] body = readResponseBody(connection, statusCode);
            return new UpstreamResponse(statusCode, connection.getContentType(), body);
        } finally {
            connection.disconnect();
        }
    }

    private String buildRequestBody(TtsVariantRequest request) {
        try {
            String upstreamText = "piper".equals(request.engine())
                ? request.normalizedText()
                : ensureTerminalPunctuation(request.normalizedText());
            return objectMapper.writeValueAsString(Map.of(
                    "text", upstreamText,
                    "voice", request.voice(),
                    "speed", request.speed(),
                    "accent", request.accent()));
        } catch (Exception ex) {
            throw new IllegalStateException("TTS 请求体序列化失败", ex);
        }
    }

    private byte[] readResponseBody(HttpURLConnection connection, int statusCode) throws java.io.IOException {
        java.io.InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return new byte[0];
        }
        try (stream) {
            return stream.readAllBytes();
        }
    }

    private boolean isValidWavResponse(UpstreamResponse response) {
        byte[] body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300 || body == null || body.length < 12) {
            return false;
        }
        String contentType = response.contentType() == null ? ""
                : response.contentType()
                        .split(";", 2)[0]
                        .trim()
                        .toLowerCase();
        if (!"audio/wav".equals(contentType) && !"audio/x-wav".equals(contentType)) {
            return false;
        }
        return body[0] == 'R'
                && body[1] == 'I'
                && body[2] == 'F'
                && body[3] == 'F'
                && body[8] == 'W'
                && body[9] == 'A'
                && body[10] == 'V'
                && body[11] == 'E';
    }

    private void validateLoopbackServiceUrl(String serviceUrl) {
        URI uri = URI.create(serviceUrl);
        String host = uri.getHost();
        if (!"127.0.0.1".equals(host) && !"localhost".equalsIgnoreCase(host)) {
            throw new AppException(TtsErrorCode.TTS_ADDRESS_NOT_LOCAL);
        }
    }

    static String buildInFlightKey(
            String normalizedText,
            String voice,
            double speed,
            String engine,
            String engineVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = normalizedText + "\n" + voice + "\n" + speed + "\n" + engine + "\n"
                    + (engineVersion == null ? "" : engineVersion);
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("TTS 瞬时请求键生成失败", ex);
        }
    }

    private record UpstreamResponse(int statusCode, String contentType, byte[] body) {
    }
}
