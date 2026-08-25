package openflash_ai_runtime.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 读取并校验 app-server 的运行时 model catalog. */
@Component
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class CodexModelCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexModelCatalog.class);

    /** 遍历 app-server 全部 model/list page 并返回可见模型. */
    public CompletionStage<Catalog> load(JsonlRpcPeer peer) {
        return load(peer::request);
    }

    CompletionStage<Catalog> load(Rpc rpc) {
        CompletableFuture<Catalog> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<JsonNode>> activeRequest = new AtomicReference<>();
        result.whenComplete((catalog, failure) -> {
            if (!result.isCancelled()) return;
            CompletableFuture<JsonNode> request = activeRequest.get();
            if (request != null) request.cancel(true);
        });
        loadPage(rpc, null, new HashSet<>(), new ArrayList<>(), result, activeRequest);
        return result;
    }

    private void loadPage(
            Rpc rpc,
            String cursor,
            Set<String> seenCursors,
            List<Model> visibleModels,
            CompletableFuture<Catalog> result,
            AtomicReference<CompletableFuture<JsonNode>> activeRequest) {
        if (result.isDone()) return;
        Map<String, Object> params = cursor == null
                ? Map.of("includeHidden", false)
                : Map.of("includeHidden", false, "cursor", cursor);
        CompletionStage<JsonNode> request;
        try {
            request = rpc.request("model/list", params);
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
            return;
        }
        CompletableFuture<JsonNode> requestFuture = request.toCompletableFuture();
        activeRequest.set(requestFuture);
        if (result.isCancelled()) {
            requestFuture.cancel(true);
            return;
        }
        request.whenComplete((response, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            try {
                Page page = parsePage(response);
                page.models.stream()
                        .filter(model -> !model.hidden)
                        .map(ParsedModel::model)
                        .forEach(visibleModels::add);
                if (page.nextCursor == null) {
                    result.complete(createCatalog(visibleModels));
                    return;
                }
                if (!seenCursors.add(page.nextCursor)) {
                    throw new ProtocolException("model/list repeated cursor");
                }
                loadPage(rpc, page.nextCursor, seenCursors, visibleModels, result, activeRequest);
            } catch (RuntimeException protocolFailure) {
                result.completeExceptionally(protocolFailure);
            }
        });
    }

    private static Page parsePage(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new ProtocolException("model/list response must be an object");
        }
        JsonNode data = response.get("data");
        if (data == null || !data.isArray()) {
            throw new ProtocolException("model/list data must be an array");
        }
        List<ParsedModel> models = new ArrayList<>();
        for (JsonNode node : data) models.add(parseModel(node));

        JsonNode nextCursorNode = response.get("nextCursor");
        String nextCursor = null;
        if (nextCursorNode != null && !nextCursorNode.isNull()) {
            if (!nextCursorNode.isTextual()) {
                throw new ProtocolException("model/list nextCursor must be text or null");
            }
            nextCursor = nextCursorNode.textValue();
        }
        return new Page(models, nextCursor);
    }

    private static ParsedModel parseModel(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ProtocolException("model/list model must be an object");
        }
        String id = requiredText(node, "id");
        String model = requiredText(node, "model");
        String displayName = requiredText(node, "displayName");
        String description = requiredText(node, "description");
        boolean hidden = requiredBoolean(node, "hidden");
        boolean defaultModel = requiredBoolean(node, "isDefault");
        String defaultReasoningEffort = requiredNonBlankText(node, "defaultReasoningEffort");
        JsonNode supportedNode = node.get("supportedReasoningEfforts");
        if (supportedNode == null || !supportedNode.isArray()) {
            throw new ProtocolException("model supportedReasoningEfforts must be an array");
        }
        List<ReasoningEffort> supported = new ArrayList<>();
        for (JsonNode effort : supportedNode) {
            if (effort == null || !effort.isObject()) {
                throw new ProtocolException("reasoning effort must be an object");
            }
            supported.add(new ReasoningEffort(
                    requiredNonBlankText(effort, "reasoningEffort"),
                    requiredText(effort, "description")));
        }
        return new ParsedModel(
                new Model(
                        id,
                        model,
                        displayName,
                        description,
                        defaultModel,
                        defaultReasoningEffort,
                        supported),
                hidden);
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new ProtocolException("model/list required text field is invalid");
        }
        return value.textValue();
    }

    private static String requiredNonBlankText(JsonNode object, String field) {
        String value = requiredText(object, field);
        if (value.isBlank()) {
            throw new ProtocolException("model/list reasoning effort is invalid");
        }
        return value;
    }

    private static boolean requiredBoolean(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isBoolean()) {
            throw new ProtocolException("model/list required boolean field is invalid");
        }
        return value.booleanValue();
    }

    private static Catalog createCatalog(List<Model> models) {
        if (models.isEmpty()) throw new ProtocolException("model/list returned no visible models");
        List<Model> defaults = models.stream().filter(Model::defaultModel).toList();
        if (defaults.size() > 1) {
            LOGGER.warn(
                    "Codex model catalog returned {} default entries; using first protocol entry",
                    defaults.size());
        }
        Model initialModel = defaults.isEmpty() ? models.get(0) : defaults.get(0);
        return new Catalog(models, initialModel);
    }

    @FunctionalInterface
    interface Rpc {
        CompletionStage<JsonNode> request(String method, Map<String, Object> params);
    }

    /** 可见模型 catalog 与新配置初始模型. */
    public record Catalog(List<Model> models, Model initialModel) {
        public Catalog {
            models = List.copyOf(models);
        }

        /** 校验保存值并补齐未显式设置的 reasoning effort. */
        public Selection validate(String modelValue, String reasoningEffort) {
            Model selected = models.stream()
                    .filter(model -> model.model.equals(modelValue))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported Codex model"));
            List<String> supported = selected.supportedReasoningEfforts.stream()
                    .map(ReasoningEffort::reasoningEffort)
                    .toList();
            String selectedEffort = reasoningEffort;
            if (selectedEffort == null) {
                selectedEffort = supported.contains("low")
                        ? "low"
                        : selected.defaultReasoningEffort;
            }
            if (!supported.contains(selectedEffort)) {
                throw new IllegalArgumentException("Unsupported Codex reasoning effort");
            }
            return new Selection(selected.model, selectedEffort);
        }
    }

    /** model 是保存/调用值; displayName 只供展示. */
    public record Model(
            String id,
            String model,
            String displayName,
            String description,
            boolean defaultModel,
            String defaultReasoningEffort,
            List<ReasoningEffort> supportedReasoningEfforts) {
        public Model {
            supportedReasoningEfforts = List.copyOf(supportedReasoningEfforts);
        }
    }

    /** 官方顺序中的一个 reasoning effort option; 值允许未来字符串. */
    public record ReasoningEffort(String reasoningEffort, String description) {}

    /** 已校验的 app-server 调用参数. */
    public record Selection(String model, String reasoningEffort) {}

    /** app-server catalog 违反协议. */
    public static final class ProtocolException extends RuntimeException {
        private ProtocolException(String message) {
            super(message);
        }
    }

    private record ParsedModel(Model model, boolean hidden) {}

    private record Page(List<ParsedModel> models, String nextCursor) {}
}
