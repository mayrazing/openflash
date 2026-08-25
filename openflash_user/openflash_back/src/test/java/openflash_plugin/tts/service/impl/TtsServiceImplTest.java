package openflash_plugin.tts.service.impl;

import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_plugin.tts.common.TtsErrorCode;
import openflash_plugin.tts.config.TtsProperties;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.service.TtsDeckSettingsService;
import openflash_plugin.tts.service.TtsService;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TtsServiceImplTest {

    private static final byte[] WAV_BYTES = new byte[] {
            'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E', 1, 2, 3, 4
    };

    /**
     * 验证发音前会把常见词典缩写展开成完整英文，页面原文不受影响。
     */
    @Test
    void normalizeSpeechTextExpandsDictionaryAbbreviations() {
        assertEquals("ask somebody to do something at someplace",
                TtsServiceImpl.normalizeSpeechText("ask sb. to do sth. at sp."));
    }

    /**
     * 验证发音前会同时处理斜杠和词典缩写，让 TTS 读出自然英文。
     */
    @Test
    void normalizeSpeechTextExpandsSlashAndDictionaryAbbreviations() {
        assertEquals("somebody or something",
                TtsServiceImpl.normalizeSpeechText("sb./sth."));
    }

    @Test
    void ensureTerminalPunctuationAddsPeriodOnlyWhenMissing() {
        assertEquals("hello.", TtsServiceImpl.ensureTerminalPunctuation("hello"));
        assertEquals("hello?", TtsServiceImpl.ensureTerminalPunctuation("hello?"));
        assertEquals("hello!", TtsServiceImpl.ensureTerminalPunctuation("hello!"));
    }

    /**
     * 验证 TTS 开关关闭时，点击发音取字节会直接返回功能关闭错误。
     */
    @Test
    void getAudioBytesThrowsFeatureDisabledWhenTtsFeatureIsOff(@TempDir Path tmp) {
        TtsServiceImpl svc = disabledFeatureService(tmp);

        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("hello"));

        assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
    }

    @Test
    void getAudioBytesReturnsWavFromCosyVoice3(@TempDir Path tmp) throws Exception {
        try (TestServer server = TestServer.responding(200, "audio/wav", WAV_BYTES)) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            byte[] result = svc.getAudioBytes("hello");

            assertArrayEquals(WAV_BYTES, result);
            assertTrue(server.lastRequestBody.contains("\"text\":\"hello.\""));
            assertTrue(server.lastRequestBody.contains("\"accent\":\"american\""));
            assertFalse(server.lastRequestBody.contains("\"engine\""));
        }
    }

    @Test
    void getAudioBytesRoutesPiperEngineToPiperService(@TempDir Path tmp) throws Exception {
        try (TestServer cosyvoice3 = TestServer.responding(200, "audio/wav", WAV_BYTES);
             TestServer piper = TestServer.responding(200, "audio/wav", WAV_BYTES)) {
            TtsProperties properties = properties(cosyvoice3.url());
            properties.setPiperServiceUrl(piper.url());
            TtsServiceImpl svc = service(tmp, properties, mock(TtsFeatureGuard.class));

            byte[] result = svc.getAudioBytes(7L, "classifier", "piper");

            assertArrayEquals(WAV_BYTES, result);
            assertEquals("", cosyvoice3.lastRequestBody);
            assertTrue(piper.lastRequestBody.contains("\"text\":\"classifier\""));
            assertFalse(piper.lastRequestBody.contains("\"text\":\"classifier.\""));
            assertTrue(piper.lastRequestBody.contains("\"speed\":0.7"));
            assertEquals("piper-1.6.0-libritts-r-medium-speaker-0",
                svc.createVariantRequest("classifier", "piper").engineVersion());
        }
    }

    @Test
    void getAudioBytesRoutesDeckDefaultModelToPiperService(@TempDir Path tmp) throws Exception {
        try (TestServer cosyvoice3 = TestServer.responding(200, "audio/wav", WAV_BYTES);
             TestServer piper = TestServer.responding(200, "audio/wav", WAV_BYTES)) {
            TtsProperties properties = properties(cosyvoice3.url());
            properties.setPiperServiceUrl(piper.url());
            TtsDeckSettingsService deckSettingsService = mock(TtsDeckSettingsService.class);
            org.mockito.Mockito.when(deckSettingsService.getForCurrentUser(11L))
                .thenReturn(new TtsDeckSettings(11L, false, false, "piper"));
            TtsServiceImpl svc = new TtsServiceImpl(
                properties, mock(TtsFeatureGuard.class), deckSettingsService, new ObjectMapper());

            byte[] result = svc.getAudioBytes(7L, 11L, "classifier");

            assertArrayEquals(WAV_BYTES, result);
            assertEquals("", cosyvoice3.lastRequestBody);
            assertTrue(piper.lastRequestBody.contains("\"text\":\"classifier\""));
        }
    }

    @Test
    void getAudioBytesUsesFixedCosyVoice3Speed(@TempDir Path tmp) throws Exception {
        try (TestServer cosyvoice3 = TestServer.responding(200, "audio/wav", WAV_BYTES)) {
            TtsServiceImpl svc = service(tmp, properties(cosyvoice3.url()), mock(TtsFeatureGuard.class));

            byte[] result = svc.getAudioBytes(7L, "classifier", "cosyvoice3");

            assertArrayEquals(WAV_BYTES, result);
            assertTrue(cosyvoice3.lastRequestBody.contains("\"speed\":0.95"));
        }
    }

    @Test
    void sequentialRequestsAreNotCachedOnServer(@TempDir Path tmp) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (TestServer server = TestServer.responding(exchange -> {
            requestCount.incrementAndGet();
            return TestResponse.wav(WAV_BYTES);
        })) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            assertArrayEquals(WAV_BYTES, svc.getAudioBytes("classifier"));
            assertArrayEquals(WAV_BYTES, svc.getAudioBytes("classifier"));

            assertEquals(2, requestCount.get());
        }
    }

    @Test
    void piperAcceptsExactlyFiveHundredCharactersWithoutAddingPunctuation(@TempDir Path tmp) {
        TtsServiceImpl svc = service(tmp, properties("http://127.0.0.1:8888/synthesize"));

        TtsService.TtsVariantRequest request = svc.createVariantRequest("a".repeat(500), "piper");

        assertNotNull(request);
        assertEquals(500, request.normalizedText().length());
    }

    @Test
    void inFlightKeySeparatesEnginesEvenWhenVersionsMatch(@TempDir Path tmp) {
        TtsProperties properties = properties("http://127.0.0.1:8888/synthesize");
        properties.setEngineVersion("same-version");
        properties.setPiperEngineVersion("same-version");
        TtsServiceImpl svc = service(tmp, properties);

        assertNotEquals(
            svc.createVariantRequest("classifier", "cosyvoice3").inFlightKey(),
            svc.createVariantRequest("classifier", "piper").inFlightKey());
    }

    @Test
    void getAudioBytesAcceptsAudioXWav(@TempDir Path tmp) throws Exception {
        try (TestServer server = TestServer.responding(200, "audio/x-wav", WAV_BYTES)) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            byte[] result = svc.getAudioBytes("hello");

            assertArrayEquals(WAV_BYTES, result);
        }
    }

    @Test
    void getAudioBytesRejectsJson(@TempDir Path tmp) throws Exception {
        try (TestServer server = TestServer.responding(200, "application/json", "{\"error\":\"bad\"}".getBytes())) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("hello"));
            assertEquals(TtsErrorCode.TTS_UPSTREAM_ERROR, ex.getErrorCode());
        }
    }

    @Test
    void getAudioBytesRejectsInvalidWav(@TempDir Path tmp) throws Exception {
        try (TestServer server = TestServer.responding(200, "audio/wav", "not wav".getBytes())) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("missing"));
            assertEquals(TtsErrorCode.TTS_UPSTREAM_ERROR, ex.getErrorCode());
        }
    }

    @Test
    void getAudioBytesRejectsUpstreamFailure(@TempDir Path tmp) throws Exception {
        try (TestServer server = TestServer.responding(500, "application/json", "{\"error\":\"bad\"}".getBytes())) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));

            AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("hello"));
            assertEquals(TtsErrorCode.TTS_UPSTREAM_ERROR, ex.getErrorCode());
        }
    }

    @Test
    void getAudioBytesThrowsOnBlankText(@TempDir Path tmp) {
        TtsServiceImpl svc = enabledService(tmp);
        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("  "));
        assertEquals(TtsErrorCode.TTS_TEXT_BLANK, ex.getErrorCode());
    }

    @Test
    void getAudioBytesThrowsOnAllSpecialChars(@TempDir Path tmp) {
        TtsServiceImpl svc = enabledService(tmp);
        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("???"));
        assertEquals(TtsErrorCode.TTS_ENGLISH_ONLY, ex.getErrorCode());
    }

    @Test
    void getAudioBytesThrowsOnChineseText(@TempDir Path tmp) {
        TtsServiceImpl svc = enabledService(tmp);
        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("你好"));
        assertEquals(TtsErrorCode.TTS_ENGLISH_ONLY, ex.getErrorCode());
    }

    @Test
    void getAudioBytesThrowsOnMixedChineseText(@TempDir Path tmp) {
        TtsServiceImpl svc = enabledService(tmp);
        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("hello你好"));
        assertEquals(TtsErrorCode.TTS_ENGLISH_ONLY, ex.getErrorCode());
    }

    @Test
    void getAudioBytesThrowsOnTooLongText(@TempDir Path tmp) {
        TtsServiceImpl svc = enabledService(tmp);
        AppException ex = assertThrows(AppException.class, () -> svc.getAudioBytes("a".repeat(501)));
        assertEquals(TtsErrorCode.TTS_TEXT_TOO_LONG, ex.getErrorCode());
    }

    /**
     * 验证一次发音请求只读取一轮动态 TTS 参数，避免连续点击发音时重复读取配置。
     */
    @Test
    void createVariantRequestReadsDynamicPropertiesOnce(@TempDir Path tmp) {
        CountingTtsProperties properties = new CountingTtsProperties(properties("http://127.0.0.1:8888/synthesize"));
        TtsServiceImpl svc = service(tmp, properties);

        svc.createVariantRequest("hello");

        assertEquals(1, properties.voiceReadCount);
        assertEquals(1, properties.speedReadCount);
        assertEquals(1, properties.accentReadCount);
        assertEquals(1, properties.engineVersionReadCount);
    }

    @Test
    void constructorRejectsNonLoopbackServiceUrl(@TempDir Path tmp) {
        TtsProperties properties = properties("http://192.168.1.10:8888/synthesize");
        AppException ex1 = assertThrows(AppException.class, () -> service(tmp, properties));
        assertEquals(TtsErrorCode.TTS_ADDRESS_NOT_LOCAL, ex1.getErrorCode());
        AppException ex2 = assertThrows(AppException.class, () -> service(tmp, properties));
        assertEquals(TtsErrorCode.TTS_ADDRESS_NOT_LOCAL, ex2.getErrorCode());
    }

    @Test
    void getAudioBytesDeduplicatesConcurrentRequestsForSameVariant(@TempDir Path tmp) throws Exception {
        CountDownLatch releaseLatch = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (TestServer server = TestServer.responding(exchange -> {
            requestCount.incrementAndGet();
            releaseLatch.await(2, TimeUnit.SECONDS);
            return TestResponse.wav(WAV_BYTES);
        })) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));
            CountDownLatch started = new CountDownLatch(2);
            AtomicInteger successCount = new AtomicInteger();
            Thread first = new Thread(() -> runGetAudioBytes(svc, "hello", started, successCount, failure));
            Thread second = new Thread(() -> runGetAudioBytes(svc, "hello", started, successCount, failure));

            first.start();
            second.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Thread.sleep(150);
            releaseLatch.countDown();
            first.join(2000);
            second.join(2000);

            assertNull(failure.get());
            assertEquals(1, requestCount.get());
            assertEquals(2, successCount.get());
        }
    }

    @Test
    void getAudioBytesLimitsConcurrentUpstreamRequests(@TempDir Path tmp) throws Exception {
        CountDownLatch releaseLatch = new CountDownLatch(1);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TtsProperties properties = properties("http://127.0.0.1");
        properties.setMaxConcurrentRequests(1);

        try (TestServer server = TestServer.responding(exchange -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
                return TestResponse.wav(WAV_BYTES);
            } finally {
                inFlight.decrementAndGet();
            }
        })) {
            properties.setServiceUrl(server.url());
            TtsServiceImpl svc = service(tmp, properties);
            Thread first = new Thread(() -> runGetAudioBytes(svc, "alpha", null, null, failure));
            Thread second = new Thread(() -> runGetAudioBytes(svc, "beta", null, null, failure));

            first.start();
            Thread.sleep(100);
            second.start();
            Thread.sleep(200);
            assertEquals(1, maxInFlight.get());
            releaseLatch.countDown();
            first.join(2000);
            second.join(2000);

            assertNull(failure.get());
        }
    }

    @Test
    void thirdDistinctRequestFailsFastWhenBoundedQueueIsFull(@TempDir Path tmp) throws Exception {
        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        AtomicReference<Throwable> thirdFailure = new AtomicReference<>();
        try (TestServer server = TestServer.responding(exchange -> {
            firstRequestEntered.countDown();
            releaseFirstRequest.await(2, TimeUnit.SECONDS);
            return TestResponse.wav(WAV_BYTES);
        })) {
            TtsProperties properties = properties(server.url());
            properties.setRequestQueueCapacity(1);
            TtsServiceImpl svc = service(tmp, properties);
            Thread first = new Thread(() -> runGetAudioBytes(svc, "alpha", null, null, firstFailure));
            Thread second = new Thread(() -> runGetAudioBytes(svc, "beta", null, null, secondFailure));
            Thread third = new Thread(() -> runGetAudioBytes(svc, "gamma", null, null, thirdFailure));

            first.start();
            assertTrue(firstRequestEntered.await(1, TimeUnit.SECONDS));
            second.start();
            assertTrue(waitUntilThreadWaits(second, 1000), "second request was not held by the bounded queue");
            third.start();
            third.join(1000);

            assertFalse(third.isAlive(), "request beyond queue capacity must fail fast");
            assertInstanceOf(AppException.class, thirdFailure.get());
            assertEquals(42901,
                    ((AppException) thirdFailure.get()).getErrorCode().value());

            releaseFirstRequest.countDown();
            first.join(2000);
            second.join(2000);
            assertNull(firstFailure.get());
            assertNull(secondFailure.get());
        } finally {
            releaseFirstRequest.countDown();
        }
    }

    @Test
    void getAudioBytesSharesFailureForSameVariantAndAllowsRetry(@TempDir Path tmp) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        try (TestServer server = TestServer.responding(exchange -> {
            int current = requestCount.incrementAndGet();
            if (current == 1) {
                firstRequestEntered.countDown();
                releaseLatch.await(2, TimeUnit.SECONDS);
                return new TestResponse(502, "application/json", "{\"error\":\"bad\"}".getBytes());
            }
            return TestResponse.wav(WAV_BYTES);
        })) {
            TtsServiceImpl svc = service(tmp, properties(server.url()));
            CountDownLatch started = new CountDownLatch(2);
            AtomicInteger upstreamFailureCount = new AtomicInteger();
            Thread first = new Thread(
                    () -> runExpectUpstreamFailure(svc, "hello", started, upstreamFailureCount, failure));
            Thread second = new Thread(
                    () -> runExpectUpstreamFailure(svc, "hello", started, upstreamFailureCount, failure));

            first.start();
            assertTrue(firstRequestEntered.await(1, TimeUnit.SECONDS));
            second.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(waitUntilThreadWaits(second, 1000), "second thread did not wait on shared in-flight request");
            releaseLatch.countDown();
            first.join(2000);
            second.join(2000);

            assertNull(failure.get());
            assertEquals(1, requestCount.get());
            assertEquals(2, upstreamFailureCount.get());
            byte[] result = svc.getAudioBytes("hello");

            assertEquals(2, requestCount.get());
            assertArrayEquals(WAV_BYTES, result);
        }
    }

    private boolean waitUntilThreadWaits(Thread thread, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (isThreadWaiting(thread)) {
                return true;
            }
            Thread.sleep(10);
        }
        return isThreadWaiting(thread);
    }

    private boolean isThreadWaiting(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
    }

    private TtsProperties properties(String serviceUrl) {
        TtsProperties properties = new TtsProperties();
        properties.setServiceUrl(serviceUrl);
        properties.setPiperServiceUrl("http://127.0.0.1:8889/synthesize");
        properties.setVoice("default");
        properties.setSpeed(0.95d);
        properties.setPiperSpeed(0.7d);
        properties.setAccent("american");
        properties.setEngineVersion("cosyvoice3-rl-fp16");
        properties.setPiperEngineVersion("piper-1.6.0-libritts-r-medium-speaker-0");
        properties.setMaxConcurrentRequests(1);
        properties.setRequestQueueCapacity(1);
        properties.setMaxConcurrentRequestsPerUser(2);
        properties.setConnectTimeoutMillis(5000L);
        properties.setRequestTimeoutMillis(10000L);
        return properties;
    }

    private static final class CountingTtsProperties extends TtsProperties {
        private int voiceReadCount;
        private int speedReadCount;
        private int accentReadCount;
        private int engineVersionReadCount;

        private CountingTtsProperties(TtsProperties source) {
            setServiceUrl(source.getServiceUrl());
            setPiperServiceUrl(source.getPiperServiceUrl());
            setVoice(source.getVoice());
            setSpeed(source.getSpeed());
            setPiperSpeed(source.getPiperSpeed());
            setAccent(source.getAccent());
            setEngineVersion(source.getEngineVersion());
            setPiperEngineVersion(source.getPiperEngineVersion());
            setMaxConcurrentRequests(source.getMaxConcurrentRequests());
            setConnectTimeoutMillis(source.getConnectTimeoutMillis());
            setRequestTimeoutMillis(source.getRequestTimeoutMillis());
        }

        @Override
        public String getVoice() {
            voiceReadCount++;
            return super.getVoice();
        }

        @Override
        public double getSpeed() {
            speedReadCount++;
            return super.getSpeed();
        }

        @Override
        public String getAccent() {
            accentReadCount++;
            return super.getAccent();
        }

        @Override
        public String getEngineVersion() {
            engineVersionReadCount++;
            return super.getEngineVersion();
        }
    }

    /**
     * 创建默认开启 TTS 开关的服务实例，保持旧测试只关注发音流程本身。
     */
    private TtsServiceImpl enabledService(Path tmp) {
        return service(tmp, properties("http://127.0.0.1:8888/synthesize"));
    }

    /**
     * 创建默认开启 TTS 开关的服务实例，供需要自定义上游地址的测试使用。
     */
    private TtsServiceImpl service(Path tmp, TtsProperties properties) {
        return service(tmp, properties, mock(TtsFeatureGuard.class));
    }

    /**
     * 创建 TTS 开关关闭的服务实例，避免测试触发真实上游发音。
     */
    private TtsServiceImpl disabledFeatureService(Path tmp) {
        TtsFeatureGuard guard = mock(TtsFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(guard).ensureTtsEnabled();
        return service(
            tmp,
            properties("http://127.0.0.1:8888/synthesize"),
            guard);
    }

    /**
     * 统一创建测试服务，显式注入功能开关，避免生产类携带测试默认值。
     */
    private TtsServiceImpl service(
            Path tmp,
            TtsProperties properties,
            TtsFeatureGuard guard) {
        return new TtsServiceImpl(
                properties,
                guard,
                defaultDeckSettingsService(),
                new ObjectMapper());
    }

    private static TtsDeckSettingsService defaultDeckSettingsService() {
        TtsDeckSettingsService service = mock(TtsDeckSettingsService.class);
        org.mockito.Mockito.when(service.getForCurrentUser(org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(invocation -> new TtsDeckSettings(
                invocation.getArgument(0), false, false, "cosyvoice3"));
        return service;
    }

    private void runGetAudioBytes(
            TtsServiceImpl svc,
            String text,
            CountDownLatch started,
            AtomicInteger successCount,
            AtomicReference<Throwable> failure) {
        if (started != null) {
            started.countDown();
        }
        try {
            svc.getAudioBytes(text);
            if (successCount != null) {
                successCount.incrementAndGet();
            }
        } catch (Exception ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private void runExpectUpstreamFailure(
            TtsServiceImpl svc,
            String text,
            CountDownLatch started,
            AtomicInteger failureCount,
            AtomicReference<Throwable> failure) {
        if (started != null) {
            started.countDown();
        }
        try {
            svc.getAudioBytes(text);
            failure.compareAndSet(null, new AssertionError("expected AppException(TTS_UPSTREAM_ERROR)"));
        } catch (AppException ex) {
            if (ex.getErrorCode() == TtsErrorCode.TTS_UPSTREAM_ERROR) {
                failureCount.incrementAndGet();
            } else {
                failure.compareAndSet(null, ex);
            }
        } catch (Exception ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private String lastRequestBody = "";
        private ExchangeHandler exchangeHandler;

        private TestServer(HttpServer server) {
            this.server = server;
        }

        static TestServer responding(int status, String contentType, byte[] body) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestServer testServer = new TestServer(server);
            server.createContext("/synthesize", exchange -> testServer.handle(exchange, status, contentType, body));
            server.start();
            return testServer;
        }

        static TestServer responding(ExchangeHandler exchangeHandler) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestServer testServer = new TestServer(server);
            testServer.exchangeHandler = exchangeHandler;
            server.createContext("/synthesize", testServer::handle);
            server.start();
            return testServer;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/synthesize";
        }

        private void handle(HttpExchange exchange, int status, String contentType, byte[] body)
                throws java.io.IOException {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            TestResponse response;
            try {
                response = exchangeHandler.handle(exchange);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                response = new TestResponse(500, "application/json", "{\"error\":\"interrupted\"}".getBytes());
            } catch (Exception ex) {
                response = new TestResponse(500, "application/json", "{\"error\":\"failed\"}".getBytes());
            }
            exchange.getResponseHeaders().add("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), response.body().length);
            exchange.getResponseBody().write(response.body());
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        TestResponse handle(HttpExchange exchange) throws Exception;
    }

    private record TestResponse(int status, String contentType, byte[] body) {

        private static TestResponse wav(byte[] body) {
            return new TestResponse(200, "audio/wav", body);
        }
    }
}
