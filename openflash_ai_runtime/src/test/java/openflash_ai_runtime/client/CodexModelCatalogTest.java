package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import openflash_ai_runtime.common.CodexLogCapture;
import org.junit.jupiter.api.Test;

class CodexModelCatalogTest {

        private static final ObjectMapper JSON = new ObjectMapper();

        @Test
        void traversesAllCursorsFiltersHiddenAndUsesModelAsStableValue() throws Exception {
                StubRpc rpc = new StubRpc(
                                page(List.of(model("internal-id", "gpt-5.4-codex", "GPT 5.4", false, true,
                                                "high", List.of("high", "medium", "low"))), "page-2"),
                                page(List.of(
                                                model("hidden-id", "hidden-model", "Hidden", true, false,
                                                                "medium", List.of("medium")),
                                                model("other-id", "gpt-5.3-codex", "GPT 5.3", false, false,
                                                                "medium", List.of("low", "medium", "x-future"))),
                                                null));

                CodexModelCatalog.Catalog catalog = await(new CodexModelCatalog().load(rpc));

                assertEquals(2, rpc.requests.size());
                assertEquals(Map.of("includeHidden", false), rpc.requests.get(0));
                assertEquals(Map.of("includeHidden", false, "cursor", "page-2"), rpc.requests.get(1));
                assertEquals(List.of("gpt-5.4-codex", "gpt-5.3-codex"),
                                catalog.models().stream().map(CodexModelCatalog.Model::model).toList());
                assertEquals("GPT 5.4", catalog.models().get(0).displayName());
                assertEquals(List.of("high", "medium", "low"), catalog.models().get(0)
                                .supportedReasoningEfforts().stream()
                                .map(CodexModelCatalog.ReasoningEffort::reasoningEffort)
                                .toList());
                assertEquals("x-future", catalog.models().get(1)
                                .supportedReasoningEfforts().get(2).reasoningEffort());
        }

        @Test
        void rejectsRepeatedCursorMissingFieldsAndEmptyVisibleCatalog() {
                CodexModelCatalog catalog = new CodexModelCatalog();
                StubRpc repeated = new StubRpc(page(List.of(model(
                                "id", "model", "Model", false, true, "medium", List.of("medium"))), "same"));
                repeated.repeatLast = true;

                assertProtocolFailure(() -> await(catalog.load(repeated)));
                assertProtocolFailure(() -> await(catalog.load(new StubRpc(pageJson(
                                "{\"data\":[{\"model\":\"missing-fields\"}],\"nextCursor\":null}")))));
                assertProtocolFailure(() -> await(catalog.load(new StubRpc(page(List.of(model(
                                "id", "hidden", "Hidden", true, true, "medium", List.of("medium"))), null)))));
        }

        @Test
        void picksFirstOfficialModelWhenNoDefaultAndFirstDefaultWhenSeveral() throws Exception {
                CodexModelCatalog.Catalog noDefault = await(new CodexModelCatalog().load(new StubRpc(page(
                                List.of(
                                                model("a", "first", "First", false, false, "low", List.of("low")),
                                                model("b", "second", "Second", false, false, "medium",
                                                                List.of("medium"))),
                                null))));
                CodexModelCatalog.Catalog multipleDefaults;
                try (CodexLogCapture logs = CodexLogCapture.capture(CodexModelCatalog.class)) {
                        multipleDefaults = await(new CodexModelCatalog().load(new StubRpc(page(
                                        List.of(
                                                        model("a", "first", "First description secret", false, true,
                                                                        "low", List.of("low")),
                                                        model("b", "second", "Second description secret", false, true,
                                                                        "medium", List.of("medium"))),
                                        null))));

                        assertEquals(1, logs.events().size());
                        ILoggingEvent warning = logs.events().get(0);
                        assertEquals(Level.WARN, warning.getLevel());
                        assertEquals(
                                        "Codex model catalog returned 2 default entries; using first protocol entry",
                                        warning.getFormattedMessage());
                        assertFalse(warning.getFormattedMessage().contains("description secret"));
                }

                assertEquals("first", noDefault.initialModel().model());
                assertEquals("first", multipleDefaults.initialModel().model());
        }

        @Test
        void validatesExplicitEffortAndChoosesLowOrModelDefault() throws Exception {
                CodexModelCatalog.Catalog catalog = await(new CodexModelCatalog().load(new StubRpc(page(
                                List.of(
                                                model("a", "with-low", "With low", false, true,
                                                                "high", List.of("high", "medium", "low")),
                                                model("b", "without-low", "Without low", false, false,
                                                                "future-default", List.of("medium", "future-default"))),
                                null))));

                assertEquals(new CodexModelCatalog.Selection("with-low", "low"),
                                catalog.validate("with-low", null));
                assertEquals(new CodexModelCatalog.Selection("without-low", "future-default"),
                                catalog.validate("without-low", null));
                assertEquals(new CodexModelCatalog.Selection("with-low", "high"),
                                catalog.validate("with-low", "high"));
                assertThrows(IllegalArgumentException.class, () -> catalog.validate("with-low", "future-default"));
                assertThrows(IllegalArgumentException.class, () -> catalog.validate("unknown", null));
        }

        @Test
        void acceptsEmptyDescriptionAllowedByProtocolSchema() throws Exception {
                JsonNode model = model("id", "value", "Display", false, true, "medium", List.of("medium"));
                ((com.fasterxml.jackson.databind.node.ObjectNode) model).put("description", "");

                CodexModelCatalog.Catalog catalog = await(
                                new CodexModelCatalog().load(new StubRpc(page(List.of(model), null))));

                assertEquals("", catalog.models().get(0).description());
        }

        private static JsonNode page(List<JsonNode> models, String nextCursor) {
                var root = JSON.createObjectNode();
                var data = root.putArray("data");
                models.forEach(data::add);
                if (nextCursor == null)
                        root.putNull("nextCursor");
                else
                        root.put("nextCursor", nextCursor);
                root.put("unknownFutureField", true);
                return root;
        }

        private static JsonNode model(
                        String id,
                        String model,
                        String displayName,
                        boolean hidden,
                        boolean isDefault,
                        String defaultEffort,
                        List<String> efforts) {
                var node = JSON.createObjectNode();
                node.put("id", id);
                node.put("model", model);
                node.put("displayName", displayName);
                node.put("description", displayName + " description");
                node.put("hidden", hidden);
                node.put("isDefault", isDefault);
                node.put("defaultReasoningEffort", defaultEffort);
                var supported = node.putArray("supportedReasoningEfforts");
                efforts.forEach(effort -> supported.addObject()
                                .put("reasoningEffort", effort)
                                .put("description", effort + " description")
                                .put("unknownFutureField", 1));
                node.put("unknownFutureField", "accepted");
                return node;
        }

        private static JsonNode pageJson(String json) {
                try {
                        return JSON.readTree(json);
                } catch (Exception failure) {
                        throw new AssertionError(failure);
                }
        }

        private static CodexModelCatalog.Catalog await(CompletionStage<CodexModelCatalog.Catalog> result)
                        throws Exception {
                return result.toCompletableFuture().get(3, TimeUnit.SECONDS);
        }

        private static void assertProtocolFailure(ThrowingCall call) {
                Exception failure = assertThrows(Exception.class, call::run);
                Throwable cause = failure;
                while (cause.getCause() != null)
                        cause = cause.getCause();
                assertEquals(CodexModelCatalog.ProtocolException.class, cause.getClass());
        }

        private interface ThrowingCall {
                void run() throws Exception;
        }

        private static final class StubRpc implements CodexModelCatalog.Rpc {
                private final List<JsonNode> responses;
                private final List<Map<String, Object>> requests = new ArrayList<>();
                private int index;
                private boolean repeatLast;

                private StubRpc(JsonNode... responses) {
                        this.responses = List.of(responses);
                }

                @Override
                public CompletionStage<JsonNode> request(String method, Map<String, Object> params) {
                        assertEquals("model/list", method);
                        requests.add(Map.copyOf(params));
                        JsonNode response = responses.get(Math.min(index++, responses.size() - 1));
                        if (repeatLast && response.path("nextCursor").isTextual()) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) response)
                                                .put("nextCursor", response.path("nextCursor").asText());
                        }
                        return CompletableFuture.completedFuture(response);
                }
        }
}
