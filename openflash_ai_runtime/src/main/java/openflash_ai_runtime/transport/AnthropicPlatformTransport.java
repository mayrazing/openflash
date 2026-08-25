package openflash_ai_runtime.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;
import openflash_ai_runtime.security.OutboundUrlValidator;
import openflash_ai_runtime.security.OutboundUrlValidator.ResolvedTarget;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Anthropic 兼容 API 的安全、可取消 HTTP transport. */
@Component
public class AnthropicPlatformTransport implements PlatformAiTransport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final long MAX_BODY_BYTES = 1024L * 1024L;
    private static final int MAX_TOKENS = 4096;

    private final OutboundUrlValidator validator;
    private final ClientFactory clientFactory;
    private final PlatformGenerationRequestRegistry requestRegistry;
    private final ConcurrentHashMap<UUID, Call> activeCalls = new ConcurrentHashMap<>();

    @Autowired
    public AnthropicPlatformTransport(
            OutboundUrlValidator validator,
            PlatformGenerationRequestRegistry requestRegistry) {
        this(validator, AnthropicPlatformTransport::productionLease, requestRegistry);
    }

    AnthropicPlatformTransport(
            OutboundUrlValidator validator,
            ClientFactory clientFactory) {
        this(validator, clientFactory, new PlatformGenerationRequestRegistry());
    }

    AnthropicPlatformTransport(
            OutboundUrlValidator validator,
            ClientFactory clientFactory,
            PlatformGenerationRequestRegistry requestRegistry) {
        this.validator = validator;
        this.clientFactory = clientFactory;
        this.requestRegistry = requestRegistry;
    }

    @Override
    public String protocol() {
        return "ANTHROPIC";
    }

    @Override
    public List<String> discoverModels(ConnectionTarget target) {
        if (target == null) throw invalidRequest();
        requireTarget(target.baseUrl(), target.apiKey());
        ResolvedTarget resolved = resolveEndpoint(target.baseUrl(), "/v1/models");
        Request request;
        try {
            request = new Request.Builder()
                    .url(resolved.uri().toString())
                    .header("x-api-key", target.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .get()
                    .build();
        } catch (IllegalArgumentException failure) {
            throw invalidRequest();
        }
        ClientLease lease;
        try {
            lease = clientFactory.create(resolved);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        try (lease; Response response = lease.client().newCall(request).execute()) {
            JsonNode root = readSuccessfulJson(response);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) throw unavailable();
            Set<String> models = new LinkedHashSet<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null && id.isTextual() && !id.textValue().isBlank()) {
                    models.add(id.textValue());
                }
            }
            if (models.isEmpty()) throw unavailable();
            return List.copyOf(models);
        } catch (openflash_ai_runtime.common.RuntimeException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    @Override
    public String generate(GenerateCommand command) {
        if (command == null || command.requestId() == null) throw invalidRequest();
        RequestState state = requestRegistry.reserve(command.requestId());
        try {
            return generate(command, state);
        } finally {
            requestRegistry.complete(state);
        }
    }

    @Override
    public String generate(GenerateCommand command, RequestState requestState) {
        if (command == null) throw invalidRequest();
        if (requestState == null
                || !java.util.Objects.equals(command.requestId(), requestState.requestId())) {
            throw invalidRequest();
        }
        GenerationRequestValidator.validateTransport(
                command.requestId(), command.model(), command.prompt(),
                command.systemPrompt(), command.temperature());
        requireTarget(command.baseUrl(), command.apiKey());
        ResolvedTarget resolved = resolveEndpoint(command.baseUrl(), "/v1/messages");
        Request request;
        try {
            request = new Request.Builder()
                    .url(resolved.uri().toString())
                    .header("x-api-key", command.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .post(RequestBody.create(generationJson(command), JSON_MEDIA))
                    .build();
        } catch (IllegalArgumentException failure) {
            throw invalidRequest();
        }
        ClientLease lease;
        Call call;
        try {
            lease = clientFactory.create(resolved);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        try {
            call = lease.client().newCall(request);
        } catch (IllegalArgumentException failure) {
            lease.close();
            throw invalidRequest();
        } catch (RuntimeException failure) {
            lease.close();
            throw unavailable();
        }
        if (!requestRegistry.bind(requestState, call::cancel)) {
            lease.close();
            throw unavailable();
        }
        if (activeCalls.putIfAbsent(command.requestId(), call) != null) {
            call.cancel();
            lease.close();
            throw invalidRequest();
        }
        try (Response response = call.execute()) {
            JsonNode root = readSuccessfulJson(response);
            JsonNode content = root.get("content");
            if (content == null || !content.isArray()) throw unavailable();
            StringBuilder text = new StringBuilder();
            for (JsonNode item : content) {
                JsonNode type = item.get("type");
                JsonNode value = item.get("text");
                if (type != null && "text".equals(type.textValue())
                        && value != null && value.isTextual()) {
                    text.append(value.textValue());
                }
            }
            if (text.isEmpty()) throw unavailable();
            return text.toString();
        } catch (openflash_ai_runtime.common.RuntimeException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        } finally {
            activeCalls.remove(command.requestId(), call);
            lease.close();
        }
    }

    @Override
    public boolean cancel(UUID requestId) {
        if (requestId == null) return false;
        if (requestRegistry.cancel(requestId)) return true;
        Call call = activeCalls.get(requestId);
        if (call == null) return false;
        call.cancel();
        return true;
    }

    private String generationJson(GenerateCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", command.model());
        payload.put("max_tokens", MAX_TOKENS);
        payload.put("messages", List.of(Map.of(
                "role", "user", "content", command.prompt())));
        if (!isBlank(command.systemPrompt())) payload.put("system", command.systemPrompt());
        if (command.temperature() != null) payload.put("temperature", command.temperature());
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw invalidRequest();
        }
    }

    private ResolvedTarget resolveEndpoint(String baseUrl, String suffix) {
        try {
            URI base = new URI(baseUrl.trim());
            String path = base.getRawPath() == null ? "" : base.getRawPath();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.endsWith("/v1") && suffix.startsWith("/v1/")) {
                path += suffix.substring(3);
            } else {
                path += suffix;
            }
            URI endpoint = new URI(
                    base.getScheme(), base.getRawUserInfo(), base.getHost(), base.getPort(),
                    path, null, base.getRawFragment());
            return validator.resolve(endpoint.toString());
        } catch (URISyntaxException | IllegalArgumentException failure) {
            throw invalidRequest();
        }
    }

    private JsonNode readSuccessfulJson(Response response) throws IOException {
        if (!response.isSuccessful()) throw unavailable();
        ResponseBody body = response.body();
        if (body == null) throw unavailable();
        BufferedSource source = body.source();
        if (source.request(MAX_BODY_BYTES + 1)) throw unavailable();
        try {
            return JSON.readTree(source.readByteArray());
        } catch (JsonProcessingException failure) {
            throw unavailable();
        }
    }

    static ClientLease productionLease(ResolvedTarget target) {
        Dns dns = host -> host.equalsIgnoreCase(target.uri().getHost())
                ? target.addresses()
                : List.of();
        OkHttpClient client = new OkHttpClient.Builder()
                .dns(dns)
                .connectionPool(new ConnectionPool(0, 1L, TimeUnit.MILLISECONDS))
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(TIMEOUT)
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .writeTimeout(TIMEOUT)
                .build();
        return ClientLease.owned(client);
    }

    private static void requireTarget(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey)) throw invalidRequest();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    private static openflash_ai_runtime.common.RuntimeException unavailable() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
    }

    @FunctionalInterface
    interface ClientFactory {
        ClientLease create(ResolvedTarget target);
    }

    /** 明确区分生产独占client与测试借用client的生命周期. */
    static final class ClientLease implements AutoCloseable {
        private final OkHttpClient client;
        private final boolean owned;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ClientLease(OkHttpClient client, boolean owned) {
            this.client = java.util.Objects.requireNonNull(client, "client");
            this.owned = owned;
        }

        static ClientLease owned(OkHttpClient client) {
            return new ClientLease(client, true);
        }

        static ClientLease borrowed(OkHttpClient client) {
            return new ClientLease(client, false);
        }

        OkHttpClient client() {
            return client;
        }

        @Override
        public void close() {
            if (!owned || !closed.compareAndSet(false, true)) return;
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }
}
