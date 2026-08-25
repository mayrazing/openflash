package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import openflash_ai_runtime.config.CodexHome;
import openflash_ai_runtime.support.CodexProcessManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexInstalledCliContractTest {

        private static final ObjectMapper JSON = new ObjectMapper();
        private static final long PROCESS_TIMEOUT_SECONDS = 30L;
        private static final long GENERATION_TIMEOUT_SECONDS = 180L;
        private static final String CONTRACT_SWITCH = "OPENFLASH_CODEX_CONTRACT_TEST";
        private static final String GENERATION_SWITCH = "OPENFLASH_CODEX_GENERATION_TEST";
        private static final String GENERATION_HOME = "OPENFLASH_CODEX_GENERATION_CODEX_HOME";
        private static final String TRANSPORT_GUARD = "Return only the requested text. Do not call tools, commands, web, apps, plugins, skills, or subagents.";
        private static final String BASE_INSTRUCTIONS = "You are a plain text generation assistant. "
                        + "Follow the developer instructions and user input.";
        private static final Map<String, Object> CLEAN_THREAD_CONFIG = Map.ofEntries(
                        Map.entry("include_permissions_instructions", false),
                        Map.entry("include_apps_instructions", false),
                        Map.entry("include_collaboration_mode_instructions", false),
                        Map.entry("include_environment_context", false),
                        Map.entry("project_doc_max_bytes", 0),
                        Map.entry("skills.include_instructions", false),
                        Map.entry("features.apps", false),
                        Map.entry("features.plugins", false),
                        Map.entry("features.tool_suggest", false),
                        Map.entry("features.multi_agent", false),
                        Map.entry("features.multi_agent_v2.root_agent_usage_hint_text", ""),
                        Map.entry("features.multi_agent_v2.multi_agent_mode_hint_text", ""),
                        Map.entry("web_search", "disabled"));

        @TempDir
        Path tempDirectory;

        @Test
        void installedCliAcceptsProductionSchemaAndStatusProtocol() throws Exception {
                Assumptions.assumeTrue(enabled(CONTRACT_SWITCH));

                assertProductionGenerationSchema(generateStableSchema());
                try (LiveAppServer server = LiveAppServer.start(
                                tempDirectory.resolve("contract-codex-home"))) {
                        JsonNode account = await(server.peer().request(
                                        "account/read", Map.of("refreshToken", false)), PROCESS_TIMEOUT_SECONDS);
                        assertTrue(account.isObject());
                        assertTrue(account.has("account"));
                        assertTrue(account.path("requiresOpenaiAuth").isBoolean());

                        CodexModelCatalog.Catalog catalog = await(
                                        new CodexModelCatalog().load(server.peer()), PROCESS_TIMEOUT_SECONDS);
                        assertFalse(catalog.models().isEmpty());
                        assertNotNull(catalog.initialModel());
                }
        }

        @Test
        void installedCliCompletesExplicitQuotaGenerationWithCleanStableThreadConfig() throws Exception {
                Assumptions.assumeTrue(enabled(GENERATION_SWITCH));

                String configuredHome = System.getenv(GENERATION_HOME);
                Assumptions.assumeTrue(
                                configuredHome != null && !configuredHome.isBlank(),
                                GENERATION_HOME + " must point to an already logged-in isolated CODEX_HOME");
                Path generationHome = Path.of(configuredHome).toAbsolutePath().normalize();
                Path defaultHome = Path.of(System.getProperty("user.home"), ".codex")
                                .toAbsolutePath()
                                .normalize();
                assertNotEquals(
                                defaultHome,
                                generationHome,
                                GENERATION_HOME + " must not point to the default ~/.codex");
                Path requestDirectory = Files.createDirectory(tempDirectory.resolve("generation-request"));
                try (LiveAppServer server = LiveAppServer.start(generationHome)) {
                        JsonNode account = await(server.peer().request(
                                        "account/read", Map.of("refreshToken", false)), PROCESS_TIMEOUT_SECONDS);
                        Assumptions.assumeTrue(
                                        account.path("account").isObject(), "Codex CLI must be logged in");

                        CodexModelCatalog.Catalog catalog = await(
                                        new CodexModelCatalog().load(server.peer()), PROCESS_TIMEOUT_SECONDS);
                        CodexModelCatalog.Selection selection = catalog.validate(catalog.initialModel().model(), null);
                        String threadId = null;
                        AutoCloseable subscription = null;
                        try {
                                JsonNode threadResponse = await(server.peer().request(
                                                "thread/start",
                                                Map.ofEntries(
                                                                Map.entry("model", selection.model()),
                                                                Map.entry(
                                                                                "cwd",
                                                                                requestDirectory.toAbsolutePath()
                                                                                                .normalize()
                                                                                                .toString()),
                                                                Map.entry("approvalPolicy", "never"),
                                                                Map.entry("sandbox", "read-only"),
                                                                Map.entry("ephemeral", true),
                                                                Map.entry("personality", "none"),
                                                                Map.entry("baseInstructions", BASE_INSTRUCTIONS),
                                                                Map.entry("developerInstructions", TRANSPORT_GUARD),
                                                                Map.entry("config", CLEAN_THREAD_CONFIG))),
                                                PROCESS_TIMEOUT_SECONDS);
                                threadId = requiredText(threadResponse.path("thread"), "id");

                                CodexTurnCollector collector = new CodexTurnCollector(
                                                1L,
                                                threadId,
                                                (generation, requestThreadId, turnId) -> server.peer().request(
                                                                "turn/interrupt",
                                                                Map.of("threadId", requestThreadId, "turnId", turnId)));
                                subscription = server.peer().onNotification(collector::accept);
                                JsonNode turnResponse = await(server.peer().request(
                                                "turn/start",
                                                Map.of(
                                                                "threadId", threadId,
                                                                "input", List.of(Map.of(
                                                                                "type", "text",
                                                                                "text",
                                                                                "Reply with exactly OPENFLASH_CODEX_CONTRACT_OK")),
                                                                "effort", selection.reasoningEffort())),
                                                PROCESS_TIMEOUT_SECONDS);
                                String turnId = requiredText(turnResponse.path("turn"), "id");
                                collector.bindTurn(threadId, turnId);

                                String result = await(collector.result(), GENERATION_TIMEOUT_SECONDS);
                                assertFalse(result.isBlank());
                        } finally {
                                closeQuietly(subscription);
                                if (threadId != null) {
                                        await(server.peer().request(
                                                        "thread/unsubscribe", Map.of("threadId", threadId)),
                                                        PROCESS_TIMEOUT_SECONDS);
                                }
                        }
                }
        }

        @Test
        void schemaMatchersIgnoreTypesAndEnumsInUnrelatedChildKeywords() throws Exception {
                JsonNode schema = JSON.readTree("""
                                {
                                  "type": "object",
                                  "properties": {
                                    "textDecoy": {"type": "string"},
                                    "listDecoy": {
                                      "type": "array",
                                      "items": {"enum": ["never"]}
                                    }
                                  }
                                }
                                """);

                assertAll(
                                () -> assertFalse(allowsType(schema, schema, "string", new HashSet<>())),
                                () -> assertFalse(allowsType(schema, schema, "array", new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(schema, schema, "never", new HashSet<>())));
        }

        @Test
        void schemaMatchersRecognizeCurrentNodeLocalReferencesAndCompositions() throws Exception {
                JsonNode schema = JSON.readTree("""
                                {
                                  "$defs": {
                                    "stringType": {"type": "string"},
                                    "allowedValue": {"enum": ["never"]}
                                  }
                                }
                                """);

                assertTrue(allowsType(
                                schema, JSON.readTree("{\"type\":\"string\"}"), "string", new HashSet<>()));
                assertTrue(allowsEnumValue(
                                schema,
                                JSON.readTree("{\"enum\":[\"never\"]}"),
                                "never",
                                new HashSet<>()));
                assertTrue(allowsType(
                                schema,
                                JSON.readTree("{\"$ref\":\"#/$defs/stringType\"}"),
                                "string",
                                new HashSet<>()));
                assertTrue(allowsEnumValue(
                                schema,
                                JSON.readTree("{\"$ref\":\"#/$defs/allowedValue\"}"),
                                "never",
                                new HashSet<>()));

                for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
                        String otherTypeBranch = "allOf".equals(keyword) ? "{}" : "{\"type\": \"object\"}";
                        String otherEnumBranch = "allOf".equals(keyword) ? "{}" : "{\"enum\": [\"always\"]}";
                        JsonNode typeComposition = JSON.readTree("""
                                        {"%s": [%s, {"$ref": "#/$defs/stringType"}]}
                                        """.formatted(keyword, otherTypeBranch));
                        JsonNode enumComposition = JSON.readTree("""
                                        {"%s": [%s, {"$ref": "#/$defs/allowedValue"}]}
                                        """.formatted(keyword, otherEnumBranch));

                        assertTrue(allowsType(schema, typeComposition, "string", new HashSet<>()), keyword);
                        assertTrue(allowsEnumValue(schema, enumComposition, "never", new HashSet<>()), keyword);
                }

                Set<String> visitedRefs = new HashSet<>();
                assertTrue(allowsType(
                                schema,
                                JSON.readTree("{\"$ref\":\"#/$defs/stringType\"}"),
                                "string",
                                visitedRefs));
                assertTrue(visitedRefs.isEmpty(), "reference traversal state must remain branch-local");
        }

        @Test
        void schemaMatchersApplyJsonSchemaCompositionSemantics() throws Exception {
                JsonNode schema = JSON.readTree("""
                                {
                                  "$defs": {
                                    "stringType": {"type": "string"},
                                    "cycleA": {"$ref": "#/$defs/cycleB"},
                                    "cycleB": {"$ref": "#/$defs/cycleA"}
                                  }
                                }
                                """);

                assertAll(
                                () -> assertFalse(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"allOf": [{"type":"string"},{"type":"object"}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertFalse(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf": [{"type":"string"},{"type":"string"}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertTrue(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"anyOf": [{"type":"string"},{"type":"object"}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertTrue(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf": [{"type":"string"},{"type":"object"}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertTrue(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"allOf": [{"type":"string"},{}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertFalse(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"type":"object","anyOf":[{"type":"string"},{}]}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertFalse(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"$ref":"#/$defs/cycleA"}
                                                                """),
                                                "string",
                                                new HashSet<>())),
                                () -> assertFalse(allowsType(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf":[
                                                                  {"$ref":"#/$defs/stringType"},
                                                                  {"$ref":"#/$defs/stringType"}
                                                                ]}
                                                                """),
                                                "string",
                                                new HashSet<>())));
        }

        @Test
        void enumMatcherAppliesJsonSchemaCompositionAndScalarConstraints() throws Exception {
                JsonNode schema = JSON.readTree("""
                                {
                                  "$defs": {
                                    "allowed": {"enum": ["never"]},
                                    "cycleA": {"$ref": "#/$defs/cycleB"},
                                    "cycleB": {"$ref": "#/$defs/cycleA"}
                                  }
                                }
                                """);

                assertAll(
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"allOf":[{"enum":["never"]},{"enum":["always"]}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf":[{"enum":["never"]},{"enum":["never"]}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertTrue(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"anyOf":[{"enum":["never"]},{"enum":["always"]}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertTrue(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf":[{"enum":["never"]},{"enum":["always"]}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertTrue(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"allOf":[{"enum":["never"]},{}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"type":"object","enum":["never"]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertTrue(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"type":"string","const":"never"}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"const":"always","anyOf":[{"enum":["never"]},{}]}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"$ref":"#/$defs/cycleA"}
                                                                """),
                                                "never",
                                                new HashSet<>())),
                                () -> assertFalse(allowsEnumValue(
                                                schema,
                                                JSON.readTree("""
                                                                {"oneOf":[
                                                                  {"$ref":"#/$defs/allowed"},
                                                                  {"$ref":"#/$defs/allowed"}
                                                                ]}
                                                                """),
                                                "never",
                                                new HashSet<>())));
        }

        private Path generateStableSchema() throws Exception {
                Path schemaDirectory = Files.createDirectory(tempDirectory.resolve("schema"));
                Process process = new ProcessBuilder(
                                "codex", "app-server", "generate-json-schema",
                                "--out", schemaDirectory.toString())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                try {
                        assertTrue(process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                        assertEquals(0, process.exitValue());
                        return schemaDirectory;
                } finally {
                        stop(process);
                }
        }

        private static void assertProductionGenerationSchema(Path schemaDirectory) throws Exception {
                JsonNode thread = JSON.readTree(
                                schemaDirectory.resolve("v2/ThreadStartParams.json").toFile());
                JsonNode turn = JSON.readTree(
                                schemaDirectory.resolve("v2/TurnStartParams.json").toFile());
                JsonNode loginParams = JSON.readTree(
                                schemaDirectory.resolve("v2/LoginAccountParams.json").toFile());
                JsonNode loginResponse = JSON.readTree(
                                schemaDirectory.resolve("v2/LoginAccountResponse.json").toFile());
                JsonNode cancelParams = JSON.readTree(
                                schemaDirectory.resolve("v2/CancelLoginAccountParams.json").toFile());
                JsonNode completed = JSON.readTree(
                                schemaDirectory.resolve("v2/AccountLoginCompletedNotification.json").toFile());

                assertFieldType(thread, "baseInstructions", "string");
                assertFieldType(thread, "config", "object");
                assertFieldType(thread, "developerInstructions", "string");
                assertFalse(thread.path("properties").has("dynamicTools"));
                assertFieldType(thread, "ephemeral", "boolean");
                assertFieldAllowsEnum(thread, "approvalPolicy", "never");
                assertFieldAllowsEnum(thread, "personality", "none");
                assertFieldAllowsEnum(thread, "sandbox", "read-only");
                assertFieldType(turn, "input", "array");
                assertFieldType(turn, "threadId", "string");
                assertFieldType(turn, "effort", "string");
                assertRequired(turn, "input");
                assertRequired(turn, "threadId");
                assertTrue(schemaContainsEnum(loginParams, "chatgptDeviceCode"));
                assertTrue(loginResponse.toString().contains("verificationUrl"));
                assertTrue(loginResponse.toString().contains("userCode"));
                assertTrue(cancelParams.toString().contains("loginId"));
                assertTrue(completed.toString().contains("success"));
        }

        private static void assertFieldType(JsonNode schema, String field, String expectedType) {
                JsonNode definition = schema.path("properties").path(field);
                assertFalse(definition.isMissingNode(), () -> "missing field " + field);
                assertTrue(
                                allowsType(schema, definition, expectedType, new HashSet<>()),
                                () -> field + " does not accept " + expectedType);
        }

        private static void assertFieldAllowsEnum(
                        JsonNode schema, String field, String expectedValue) {
                JsonNode definition = schema.path("properties").path(field);
                assertFalse(definition.isMissingNode(), () -> "missing field " + field);
                assertTrue(
                                allowsEnumValue(schema, definition, expectedValue, new HashSet<>()),
                                () -> field + " does not accept " + expectedValue);
        }

        private static boolean allowsType(
                        JsonNode schema, JsonNode node, String expectedType, Set<String> visitedRefs) {
                List<JsonNode> candidates = new ArrayList<>();
                addCandidate(candidates, representativeValue(expectedType));
                collectScalarCandidates(
                                schema, node, expectedType, new HashSet<>(visitedRefs), candidates);
                for (JsonNode candidate : candidates) {
                        if (matchesSchema(schema, node, candidate, new HashSet<>(visitedRefs)))
                                return true;
                }
                return false;
        }

        private static boolean allowsEnumValue(
                        JsonNode schema, JsonNode node, String expectedValue, Set<String> visitedRefs) {
                return matchesSchema(
                                schema,
                                node,
                                JSON.getNodeFactory().textNode(expectedValue),
                                new HashSet<>(visitedRefs));
        }

        private static boolean matchesSchema(
                        JsonNode schema, JsonNode node, JsonNode value, Set<String> visitedRefs) {
                if (node.isBoolean())
                        return node.booleanValue();
                if (!node.isObject() || !matchesScalarConstraints(node, value))
                        return false;

                JsonNode reference = node.get("$ref");
                if (reference != null) {
                        if (!reference.isTextual())
                                return false;
                        String referenceValue = reference.textValue();
                        Set<String> branchRefs = new HashSet<>(visitedRefs);
                        if (!branchRefs.add(referenceValue)
                                        || !matchesSchema(
                                                        schema,
                                                        resolveLocalReference(schema, referenceValue),
                                                        value,
                                                        branchRefs))
                                return false;
                }

                JsonNode anyOf = node.get("anyOf");
                if (anyOf != null && (!anyOf.isArray()
                                || countMatchingBranches(schema, anyOf, value, visitedRefs) < 1))
                        return false;

                JsonNode oneOf = node.get("oneOf");
                if (oneOf != null && (!oneOf.isArray()
                                || countMatchingBranches(schema, oneOf, value, visitedRefs) != 1))
                        return false;

                JsonNode allOf = node.get("allOf");
                if (allOf != null) {
                        if (!allOf.isArray())
                                return false;
                        for (JsonNode branch : allOf) {
                                if (!matchesSchema(schema, branch, value, new HashSet<>(visitedRefs)))
                                        return false;
                        }
                }
                return true;
        }

        private static boolean matchesScalarConstraints(JsonNode node, JsonNode value) {
                JsonNode type = node.get("type");
                if (type != null && !allowsValueType(type, value))
                        return false;

                JsonNode enumValues = node.get("enum");
                if (enumValues != null && (!enumValues.isArray() || !containsValue(enumValues, value))) {
                        return false;
                }

                JsonNode constant = node.get("const");
                return constant == null || constant.equals(value);
        }

        private static int countMatchingBranches(
                        JsonNode schema, JsonNode branches, JsonNode value, Set<String> visitedRefs) {
                int matches = 0;
                for (JsonNode branch : branches) {
                        if (matchesSchema(schema, branch, value, new HashSet<>(visitedRefs)))
                                matches++;
                }
                return matches;
        }

        private static boolean allowsValueType(JsonNode type, JsonNode value) {
                if (type.isTextual())
                        return valueHasType(value, type.textValue());
                if (!type.isArray())
                        return false;
                for (JsonNode candidateType : type) {
                        if (candidateType.isTextual() && valueHasType(value, candidateType.textValue())) {
                                return true;
                        }
                }
                return false;
        }

        private static boolean valueHasType(JsonNode value, String expectedType) {
                return switch (expectedType) {
                        case "null" -> value.isNull();
                        case "boolean" -> value.isBoolean();
                        case "object" -> value.isObject();
                        case "array" -> value.isArray();
                        case "number" -> value.isNumber();
                        case "integer" -> value.isIntegralNumber();
                        case "string" -> value.isTextual();
                        default -> false;
                };
        }

        private static JsonNode representativeValue(String expectedType) {
                return switch (expectedType) {
                        case "null" -> JSON.getNodeFactory().nullNode();
                        case "boolean" -> JSON.getNodeFactory().booleanNode(false);
                        case "object" -> JSON.createObjectNode();
                        case "array" -> JSON.createArrayNode();
                        case "number" -> JSON.getNodeFactory().numberNode(0.5d);
                        case "integer" -> JSON.getNodeFactory().numberNode(0);
                        case "string" -> JSON.getNodeFactory().textNode("");
                        default -> JSON.getNodeFactory().missingNode();
                };
        }

        private static void collectScalarCandidates(
                        JsonNode schema,
                        JsonNode node,
                        String expectedType,
                        Set<String> visitedRefs,
                        List<JsonNode> candidates) {
                if (!node.isObject())
                        return;

                JsonNode constant = node.get("const");
                if (constant != null && valueHasType(constant, expectedType)) {
                        addCandidate(candidates, constant);
                }
                JsonNode enumValues = node.get("enum");
                if (enumValues != null && enumValues.isArray()) {
                        for (JsonNode enumValue : enumValues) {
                                if (valueHasType(enumValue, expectedType))
                                        addCandidate(candidates, enumValue);
                        }
                }

                JsonNode reference = node.get("$ref");
                if (reference != null && reference.isTextual()) {
                        String referenceValue = reference.textValue();
                        Set<String> branchRefs = new HashSet<>(visitedRefs);
                        if (branchRefs.add(referenceValue)) {
                                collectScalarCandidates(
                                                schema,
                                                resolveLocalReference(schema, referenceValue),
                                                expectedType,
                                                branchRefs,
                                                candidates);
                        }
                }
                for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
                        JsonNode branches = node.get(keyword);
                        if (branches == null || !branches.isArray())
                                continue;
                        for (JsonNode branch : branches) {
                                collectScalarCandidates(
                                                schema,
                                                branch,
                                                expectedType,
                                                new HashSet<>(visitedRefs),
                                                candidates);
                        }
                }
        }

        private static void addCandidate(List<JsonNode> candidates, JsonNode candidate) {
                if (!candidate.isMissingNode() && !candidates.contains(candidate))
                        candidates.add(candidate);
        }

        private static boolean containsValue(JsonNode array, JsonNode expected) {
                for (JsonNode value : array) {
                        if (expected.equals(value))
                                return true;
                }
                return false;
        }

        private static JsonNode resolveLocalReference(JsonNode schema, String reference) {
                assertTrue(reference.startsWith("#/"), () -> "non-local schema reference " + reference);
                JsonNode resolved = schema.at(reference.substring(1));
                assertFalse(resolved.isMissingNode(), () -> "unresolved schema reference " + reference);
                return resolved;
        }

        private static boolean containsText(JsonNode array, String expected) {
                for (JsonNode value : array) {
                        if (value.isTextual() && expected.equals(value.textValue()))
                                return true;
                }
                return false;
        }

        private static boolean schemaContainsEnum(JsonNode schema, String expected) {
                if (schema.isObject()) {
                        JsonNode values = schema.get("enum");
                        if (values != null && values.isArray() && containsText(values, expected))
                                return true;
                }
                if (!schema.isContainerNode())
                        return false;
                for (JsonNode child : schema) {
                        if (schemaContainsEnum(child, expected))
                                return true;
                }
                return false;
        }

        private static void assertRequired(JsonNode schema, String field) {
                assertTrue(containsText(schema.path("required"), field), () -> "optional field " + field);
        }

        private static String requiredText(JsonNode object, String field) {
                JsonNode value = object.get(field);
                assertNotNull(value, () -> "missing response field " + field);
                assertTrue(value.isTextual() && !value.textValue().isBlank());
                return value.textValue();
        }

        private static <T> T await(CompletionStage<T> stage, long timeoutSeconds) throws Exception {
                return stage.toCompletableFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        }

        private static boolean enabled(String environmentVariable) {
                return "true".equalsIgnoreCase(System.getenv(environmentVariable));
        }

        private static void closeQuietly(AutoCloseable closeable) {
                if (closeable == null)
                        return;
                try {
                        closeable.close();
                } catch (Exception ignored) {
                        // The process-level finally block remains the cleanup authority.
                }
        }

        private static void stop(Process process) {
                if (!process.isAlive())
                        return;
                process.destroy();
                try {
                        if (!process.waitFor(5, TimeUnit.SECONDS))
                                process.destroyForcibly();
                } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                }
        }

        private record LiveAppServer(CodexProcessManager manager, JsonlRpcPeer peer)
                        implements AutoCloseable {

                private static LiveAppServer start(Path isolatedHome) throws Exception {
                        CodexHome home = new CodexHome(
                                        key -> isolatedHome.toAbsolutePath().normalize().toString(),
                                        Path.of(System.getProperty("user.home")));
                        CodexProcessManager manager = new CodexProcessManager(home);
                        try {
                                CodexProcessManager.CodexConnection connection = await(
                                                manager.connection(), PROCESS_TIMEOUT_SECONDS);
                                return new LiveAppServer(manager, connection.peer());
                        } catch (Exception failure) {
                                manager.shutdown();
                                throw failure;
                        }
                }

                @Override
                public void close() {
                        manager.shutdown();
                }
        }
}
