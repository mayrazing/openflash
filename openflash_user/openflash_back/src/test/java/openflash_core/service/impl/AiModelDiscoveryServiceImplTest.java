package openflash_core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import openflash_core.common.AiErrorCode;
import openflash_core.common.AppException;
import openflash_core.security.OutboundUrlValidator;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.AuthStyle;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.ModelListTransport;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.ModelOption;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.UpstreamAttemptFailed;
import org.junit.jupiter.api.Test;

/** 单元测试：用 fake transport + stub validator 覆盖候选顺序、解析、错误映射。 */
class AiModelDiscoveryServiceImplTest {

    private final FakeTransport transport = new FakeTransport();
    private final OutboundUrlValidator validator = new StubValidator();
    private final AiModelDiscoveryServiceImpl service =
            new AiModelDiscoveryServiceImpl(validator, transport);

    @Test
    void discoversAnthropicModelsFirst() {
        transport.reply(
                "https://api.anthropic.com/v1/models",
                200,
                "{\"data\":[{\"id\":\"claude-sonnet\"},{\"id\":\"claude-haiku\"}]}");
        List<ModelOption> models = service.discover("https://api.anthropic.com", "sk");
        assertEquals(List.of("claude-sonnet", "claude-haiku"), ids(models));
        assertEquals(AuthStyle.ANTHROPIC, transport.requests().get(0).authStyle());
    }

    @Test
    void exposesEffortLevelsDeclaredByEachAnthropicModel() {
        transport.reply(
                "https://api.anthropic.com/v1/models",
                200,
                """
                {"data":[{"id":"claude-opus","capabilities":{"effort":{
                  "supported":true,"low":{"supported":true},"medium":{"supported":false},
                  "high":{"supported":true},"xhigh":{"supported":false},
                  "max":{"supported":true}}}}]}
                """);

        ModelOption model = service.discover("https://api.anthropic.com", "sk").get(0);
        JsonNode json = new ObjectMapper().valueToTree(model);

        assertEquals("[\"low\",\"high\",\"max\"]",
                json.path("supportedReasoningEfforts").toString());
    }

    @Test
    void fallsBackToDeepSeekOnSameOrigin() {
        transport.reply("https://api.deepseek.com/anthropic/v1/models", 404, "{}");
        transport.reply(
                "https://api.deepseek.com/models",
                200,
                "{\"data\":[{\"id\":\"deepseek-v4-flash\"}]}");
        List<ModelOption> models = service.discover("https://api.deepseek.com/anthropic", "sk");
        assertEquals(List.of("deepseek-v4-flash"), ids(models));
        assertEquals(AuthStyle.BEARER, transport.requests().get(1).authStyle());
    }

    @Test
    void stripsKnownCompatibilitySuffixesBeforeBearerFallback() {
        List<String> suffixes = List.of(
                "/api/claudecode",
                "/api/anthropic",
                "/apps/anthropic",
                "/api/coding",
                "/claudecode",
                "/anthropic",
                "/step_plan",
                "/coding",
                "/claude");

        for (int i = 0; i < suffixes.size(); i++) {
            String root = "https://provider" + i + ".example.com";
            String baseUrl = root + suffixes.get(i);
            transport.reply(baseUrl + "/v1/models", 404, "{}");
            transport.reply(
                    root + "/v1/models",
                    200,
                    "{\"data\":[{\"id\":\"model-" + i + "\"}]}");

            List<ModelOption> models = service.discover(baseUrl, "sk");

            assertEquals(List.of("model-" + i), ids(models));
            assertEquals(root + "/v1/models", transport.requests().get(i * 3 + 2).url());
            assertEquals(AuthStyle.BEARER, transport.requests().get(i * 3 + 2).authStyle());
        }
    }

    @Test
    void triesRootModelsAfterRootV1Models() {
        transport.reply(
                "https://ark.cn-beijing.volces.com/api/coding/v1/models",
                AuthStyle.ANTHROPIC,
                401,
                "{}");
        transport.reply(
                "https://ark.cn-beijing.volces.com/api/coding/v1/models",
                AuthStyle.BEARER,
                404,
                "{}");
        transport.reply("https://ark.cn-beijing.volces.com/v1/models", 404, "{}");
        transport.reply(
                "https://ark.cn-beijing.volces.com/models",
                200,
                "{\"data\":[{\"id\":\"ark-code-latest\"}]}");

        List<ModelOption> models = service.discover(
                "https://ark.cn-beijing.volces.com/api/coding", "sk");

        assertEquals(List.of("ark-code-latest"), ids(models));
        assertEquals(
                List.of(
                        "https://ark.cn-beijing.volces.com/api/coding/v1/models",
                        "https://ark.cn-beijing.volces.com/api/coding/v1/models",
                        "https://ark.cn-beijing.volces.com/v1/models",
                        "https://ark.cn-beijing.volces.com/models"),
                transport.requests().stream().map(FakeTransport.RecordedRequest::url).toList());
    }

    @Test
    void retriesOriginalModelsUrlWithBearerAuthentication() {
        String modelsUrl = "https://ark.cn-beijing.volces.com/api/coding/v1/models";
        transport.reply(modelsUrl, AuthStyle.ANTHROPIC, 401, "{}");
        transport.reply(
                modelsUrl,
                AuthStyle.BEARER,
                200,
                "{\"data\":[{\"id\":\"deepseek-v4-pro-260425\"}]}");

        List<ModelOption> models = service.discover(
                "https://ark.cn-beijing.volces.com/api/coding", "sk");

        assertEquals(List.of("deepseek-v4-pro-260425"), ids(models));
        assertEquals(
                List.of(AuthStyle.ANTHROPIC, AuthStyle.BEARER),
                transport.requests().stream().map(FakeTransport.RecordedRequest::authStyle).toList());
    }

    @Test
    void rejectsEmptyOrMalformedResponses() {
        transport.replyDefault(200, "{\"data\":[]}");
        AppException ex = assertThrows(
                AppException.class, () -> service.discover("https://api.example.com", "sk"));
        assertSame(AiErrorCode.AI_MODEL_DISCOVERY_FAILED, ex.getErrorCode());
    }

    @Test
    void anthropicModelsUrlAppendsV1Models() {
        transport.reply(
                "https://api.anthropic.com/v1/models",
                200,
                "{\"data\":[{\"id\":\"claude-x\"}]}");
        service.discover("https://api.anthropic.com", "sk");
        assertEquals("https://api.anthropic.com/v1/models", transport.requests().get(0).url());
    }

    @Test
    void anthropicModelsUrlPreservesExistingV1() {
        transport.reply(
                "https://api.example.com/anthropic/v1/models",
                200,
                "{\"data\":[{\"id\":\"m1\"}]}");
        service.discover("https://api.example.com/anthropic/v1", "sk");
        assertEquals(
                "https://api.example.com/anthropic/v1/models", transport.requests().get(0).url());
    }

    @Test
    void nonAnthropicSuffixSkipsFallback() {
        transport.replyDefault(404, "{}");
        AppException ex = assertThrows(
                AppException.class, () -> service.discover("https://api.example.com", "sk"));
        assertSame(AiErrorCode.AI_MODEL_DISCOVERY_FAILED, ex.getErrorCode());
        // 仅 1 次候选请求；不触发 fallback。
        assertEquals(1, transport.requests().size());
    }

    @Test
    void validatorRejectionMapsToInvalidUrl() {
        OutboundUrlValidator rejecting = new OutboundUrlValidator() {
            @Override
            public ResolvedTarget resolve(String rawUrl) {
                throw new IllegalArgumentException("blocked");
            }
        };
        AiModelDiscoveryServiceImpl r =
                new AiModelDiscoveryServiceImpl(rejecting, transport);
        AppException ex = assertThrows(
                AppException.class, () -> r.discover("https://api.example.com", "sk"));
        assertSame(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL, ex.getErrorCode());
    }

    @Test
    void parsesIdsIgnoringNonStringIds() {
        transport.reply(
                "https://api.anthropic.com/v1/models",
                200,
                "{\"data\":[{\"id\":\"a\"},{\"id\":null},{\"id\":\"\"},{\"id\":\"b\"}]}");
        List<ModelOption> models = service.discover("https://api.anthropic.com", "sk");
        assertEquals(List.of("a", "b"), ids(models));
    }

    @Test
    void discardsCandidateWhenJsonMalformedAndContinues() {
        transport.reply("https://api.deepseek.com/anthropic/v1/models", 200, "not json");
        transport.reply(
                "https://api.deepseek.com/models", 200, "{\"data\":[{\"id\":\"x\"}]}");
        List<ModelOption> models = service.discover("https://api.deepseek.com/anthropic", "sk");
        assertEquals(List.of("x"), ids(models));
    }

    private static List<String> ids(List<ModelOption> models) {
        return models.stream().map(ModelOption::id).toList();
    }

    /** 假 transport：按 URL 字符串匹配预设响应，记录调用顺序。 */
    static final class FakeTransport implements ModelListTransport {
        private final Map<RequestKey, Reply> replies = new HashMap<>();
        private Reply def;
        private final List<RecordedRequest> requests = new ArrayList<>();

        void reply(String url, int status, String body) {
            reply(url, null, status, body);
        }

        void reply(String url, AuthStyle authStyle, int status, String body) {
            replies.put(new RequestKey(url, authStyle), new Reply(status, body));
        }

        void replyDefault(int status, String body) {
            def = new Reply(status, body);
        }

        List<RecordedRequest> requests() {
            return requests;
        }

        @Override
        public String get(OutboundUrlValidator.ResolvedTarget target, String apiKey, AuthStyle style) {
            String url = target.uri().toString();
            requests.add(new RecordedRequest(url, style));
            Reply r = replies.getOrDefault(
                    new RequestKey(url, style),
                    replies.getOrDefault(new RequestKey(url, null), def));
            if (r == null || r.status < 200 || r.status >= 300) throw new UpstreamAttemptFailed();
            return r.body;
        }

        record RequestKey(String url, AuthStyle authStyle) {}

        record Reply(int status, String body) {}

        record RecordedRequest(String url, AuthStyle authStyle) {}
    }

    /** 假 validator：直接构造 ResolvedTarget，绕过真实 DNS 与公网校验。 */
    static final class StubValidator extends OutboundUrlValidator {
        @Override
        public ResolvedTarget resolve(String rawUrl) {
            try {
                return new ResolvedTarget(
                        URI.create(rawUrl), List.of(InetAddress.getByName("8.8.8.8")));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
