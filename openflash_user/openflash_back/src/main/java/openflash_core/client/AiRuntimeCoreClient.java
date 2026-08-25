package openflash_core.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import openflash_core.common.AiErrorCode;
import openflash_core.common.CodexAppException;
import openflash_core.config.AiProperties;
import openflash_core.config.AiRuntimeCoreProperties;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import tools.jackson.databind.JsonNode;

/** 只调用 ai_runtime 的 generic core-scope platform AI API. */
@Component
public class AiRuntimeCoreClient {

    public static final String CODEX_OFFERING_KEY = "platform-codex-cli";

    private static final String CORE_HEADER = "X-OpenFlash-Ai-Runtime-Core-Token";
    private static final String PLATFORM_PATH = "/api/internal/core/platform-ai";

    private final AiRuntimeCoreProperties properties;
    private final RestClient restClient;
    private final RestClient generationClient;

    public AiRuntimeCoreClient(AiRuntimeCoreProperties properties) {
        this.properties = properties;
        this.restClient = client(properties.getReadTimeoutMillis());
        this.generationClient = client(properties.getGenerationTimeoutMillis());
    }

    public List<OfferingSnapshot> listOfferings(long userId) {
        requireUser(userId);
        JsonNode body = request(restClient, HttpMethod.GET,
                PLATFORM_PATH + "/offerings?userId=" + userId, null);
        if (!body.isArray()) throw unavailable();
        List<OfferingSnapshot> result = new ArrayList<>();
        for (JsonNode offering : body) {
            String sourceText = requiredText(offering, "source");
            AiSource source;
            try {
                source = AiSource.valueOf(sourceText);
            } catch (IllegalArgumentException failure) {
                throw unavailable();
            }
            result.add(new OfferingSnapshot(
                    requiredText(offering, "offeringKey"), source,
                    requiredText(offering, "kind"), requiredText(offering, "protocol"),
                    optionalText(offering, "modelKey"), requiredText(offering, "runtimeStatus"),
                    requiredBoolean(offering, "accessGranted"),
                    requiredBoolean(offering, "enabled")));
        }
        return List.copyOf(result);
    }

    public ModelsSnapshot listModels(long userId, String offeringKey) {
        requireOfferingScope(userId, offeringKey);
        JsonNode body = request(restClient, HttpMethod.GET,
                PLATFORM_PATH + "/offerings/{offeringKey}/models?userId={userId}",
                Map.of("offeringKey", offeringKey, "userId", userId), null);
        String runtimeStatus = requiredText(body, "runtimeStatus");
        JsonNode modelsNode = body.get("models");
        if (modelsNode == null || !modelsNode.isArray()) throw unavailable();
        List<ModelSnapshot> models = new ArrayList<>();
        for (JsonNode model : modelsNode) {
            models.add(model(model));
        }
        ModelSnapshot initial = models.stream().filter(ModelSnapshot::defaultModel)
                .findFirst().orElse(models.isEmpty() ? null : models.get(0));
        return new ModelsSnapshot(runtimeStatus, models, initial);
    }

    /** 将统一平台选择转为 generic runtime generation 请求. */
    public String generate(
            long userId,
            ActiveAiSelectionDto active,
            String prompt,
            AiProperties.AiProfile profile) {
        requireUser(userId);
        if (active == null || active.source() != AiSource.PLATFORM
                || isBlank(active.offeringKey()) || profile == null
                || isBlank(prompt) || isBlank(active.model())) {
            throw unavailable();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("userId", userId);
        payload.put("offeringKey", active.offeringKey());
        payload.put("prompt", prompt);
        payload.put("model", active.model());
        payload.put("systemPrompt", profile.getSystem());
        payload.put("temperature", profile.getTemperature());
        payload.put("reasoningEffort", active.reasoningEffort());
        JsonNode body = request(
            generationClient, HttpMethod.POST, PLATFORM_PATH + "/generations", payload);
        String text = requiredText(body, "content");
        if (text.isBlank()) throw unavailable();
        return text;
    }

    private RestClient client(int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(duration(properties.getConnectTimeoutMillis()));
        requestFactory.setReadTimeout(duration(readTimeoutMillis));
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().set(CORE_HEADER, requireToken());
                return execution.execute(request, body);
            })
            .build();
    }

    private JsonNode request(
            RestClient client, HttpMethod method, String path, Object requestBody) {
        return request(client, method, path, Map.of(), requestBody);
    }

    private JsonNode request(
            RestClient client, HttpMethod method, String path,
            Map<String, ?> uriVariables, Object requestBody) {
        try {
            RestClient.RequestBodySpec request = client.method(method).uri(path, uriVariables);
            if (requestBody != null) request.body(requestBody);
            JsonNode body = request.retrieve().body(JsonNode.class);
            if (body == null || (!body.isObject() && !body.isArray())) throw unavailable();
            return body;
        } catch (CodexAppException failure) {
            throw failure;
        } catch (RestClientException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private ModelSnapshot model(JsonNode value) {
        if (value == null || !value.isObject()) throw unavailable();
        JsonNode effortsNode = value.get("supportedReasoningEfforts");
        if (effortsNode == null || !effortsNode.isArray()) throw unavailable();
        List<ReasoningEffortSnapshot> efforts = new ArrayList<>();
        for (JsonNode effort : effortsNode) {
            efforts.add(new ReasoningEffortSnapshot(
                requiredText(effort, "reasoningEffort"),
                requiredText(effort, "description")));
        }
        JsonNode defaultModel = value.get("defaultModel");
        if (defaultModel == null || !defaultModel.isBoolean()) throw unavailable();
        String model = requiredText(value, "model");
        String defaultReasoningEffort = nullableText(value, "defaultReasoningEffort");
        if (!efforts.isEmpty() && isBlank(defaultReasoningEffort)) throw unavailable();
        return new ModelSnapshot(
            model,
            model,
            requiredText(value, "displayName"),
            requiredText(value, "description"),
            defaultModel.booleanValue(),
            defaultReasoningEffort,
            efforts);
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isString()) throw unavailable();
        String text = value.asString();
        rejectTokenEcho(text);
        return text;
    }

    private String optionalText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw unavailable();
        return value.asString();
    }

    private String nullableText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || (!value.isNull() && !value.isString())) throw unavailable();
        if (value.isNull()) return null;
        String text = value.asString();
        rejectTokenEcho(text);
        return text;
    }

    private boolean requiredBoolean(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isBoolean()) throw unavailable();
        return value.booleanValue();
    }

    private void requireOfferingScope(long userId, String offeringKey) {
        if (userId <= 0 || isBlank(offeringKey)) throw unavailable();
    }

    private void requireUser(long userId) {
        if (userId <= 0) throw unavailable();
    }

    private String requireToken() {
        String token = properties.getCoreToken();
        if (isBlank(token)) throw unavailable();
        return token;
    }

    private void rejectTokenEcho(String value) {
        String token = properties.getCoreToken();
        if (isBlank(token) || value.contains(token)) throw unavailable();
    }

    private static Duration duration(int millis) {
        if (millis <= 0) throw unavailable();
        return Duration.ofMillis(millis);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static CodexAppException unavailable() {
        return new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
    }

    public record OfferingSnapshot(
            String offeringKey, AiSource source, String kind, String protocol,
            String modelKey, String runtimeStatus, boolean accessGranted, boolean enabled) {
    }

    public record ModelsSnapshot(
            String runtimeStatus, List<ModelSnapshot> models, ModelSnapshot initialModel) {
        public ModelsSnapshot(List<ModelSnapshot> models, ModelSnapshot initialModel) {
            this("AVAILABLE", models, initialModel);
        }

        public ModelsSnapshot(String runtimeStatus, List<ModelSnapshot> models) {
            this(runtimeStatus, models, models.stream().filter(ModelSnapshot::defaultModel)
                    .findFirst().orElse(models.isEmpty() ? null : models.get(0)));
        }

        public ModelsSnapshot {
            models = List.copyOf(models);
        }
    }

    public record ModelSnapshot(
            String id,
            String model,
            String displayName,
            String description,
            boolean defaultModel,
            String defaultReasoningEffort,
            List<ReasoningEffortSnapshot> supportedReasoningEfforts) {
        public ModelSnapshot {
            supportedReasoningEfforts = List.copyOf(supportedReasoningEfforts);
        }
    }

    public record ReasoningEffortSnapshot(String reasoningEffort, String description) {
    }
}
