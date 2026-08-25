package openflash_core.client;

import openflash_core.config.AiRuntimeCoreProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import openflash_core.common.CodexAppException;
import openflash_core.config.AiProperties;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;

class AiRuntimeCoreClientTest {

    private MockWebServer server;

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.shutdown();
    }

    @Test
    void offeringsAndModelsUseOnlyGenericCorePathsAndExplicitSource() throws Exception {
        server.enqueue(json("""
                [{"offeringKey":"platform-codex-cli","source":"PLATFORM","kind":"CLI",
                  "protocol":"CODEX_APP_SERVER","modelKey":null,"runtimeStatus":"AVAILABLE",
                  "accessGranted":true,"enabled":true}]
                """));
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[{"model":"gpt-5.4",
                  "displayName":"GPT-5.4","description":"safe","defaultModel":true,
                  "defaultReasoningEffort":"high","supportedReasoningEfforts":[
                    {"reasoningEffort":"high","description":"High"}]}]}
                """));
        AiRuntimeCoreClient client = client();

        assertThat(client.listOfferings(7L).get(0).source()).isEqualTo(AiSource.PLATFORM);
        assertThat(client.listModels(7L, "platform-codex-cli").models().get(0).model())
                .isEqualTo("gpt-5.4");

        assertThat(server.takeRequest().getPath())
                .isEqualTo("/api/internal/core/platform-ai/offerings?userId=7");
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/api/internal/core/platform-ai/offerings/platform-codex-cli/models?userId=7");
    }

    @Test
    void modelsAcceptNonCodexOfferingAndEncodeItAsOnePathSegment() throws Exception {
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[]}
                """));

        assertThat(client().listModels(7L, "platform-local cli").models()).isEmpty();

        assertThat(server.takeRequest().getPath()).isEqualTo(
                "/api/internal/core/platform-ai/offerings/platform-local%20cli/models?userId=7");
    }

    @Test
    void fixedApiModelAcceptsNullableReasoningContract() {
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[{"model":"claude-sonnet-4-5",
                  "displayName":"claude-sonnet-4-5","description":"","defaultModel":true,
                  "defaultReasoningEffort":null,"supportedReasoningEfforts":[]}]}
                """));

        AiRuntimeCoreClient.ModelSnapshot model = client()
                .listModels(7L, "platform-anthropic-api")
                .models().get(0);

        assertThat(model.model()).isEqualTo("claude-sonnet-4-5");
        assertThat(model.defaultReasoningEffort()).isNull();
        assertThat(model.supportedReasoningEfforts()).isEmpty();
    }

    @Test
    void cliModelRejectsNullableDefaultEffortWhenEffortsExist() {
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[{"model":"gpt-5.4",
                  "displayName":"GPT-5.4","description":"safe","defaultModel":true,
                  "defaultReasoningEffort":null,"supportedReasoningEfforts":[
                    {"reasoningEffort":"high","description":"High"}]}]}
                """));

        assertThatThrownBy(() -> client().listModels(7L, "platform-codex-cli"))
                .isInstanceOf(CodexAppException.class);
    }

    @Test
    void modelRejectsMissingDefaultReasoningField() {
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[{"model":"fixed-model",
                  "displayName":"fixed-model","description":"","defaultModel":true,
                  "supportedReasoningEfforts":[]}]}
                """));

        assertThatThrownBy(() -> client().listModels(7L, "platform-api"))
                .isInstanceOf(CodexAppException.class);
    }

    @Test
    void modelRejectsCoreTokenInDefaultEffort() {
        server.enqueue(json("""
                {"runtimeStatus":"AVAILABLE","models":[{"model":"fixed-model",
                  "displayName":"fixed-model","description":"","defaultModel":true,
                  "defaultReasoningEffort":"core-token","supportedReasoningEfforts":[]}]}
                """));

        assertThatThrownBy(() -> client().listModels(7L, "platform-api"))
                .isInstanceOf(CodexAppException.class);
    }

    @Test
    void generationSendsUserAndOfferingToGenericEndpoint() throws Exception {
        server.enqueue(json("{\"content\":\"answer\"}"));
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setSystem("system");
        profile.setTemperature(0.2);
        ActiveAiSelectionDto active = new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-codex-cli",
                "CODEX_APP_SERVER", "gpt-5.4", "high");

        assertThat(client().generate(7L, active, "prompt", profile)).isEqualTo("answer");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/api/internal/core/platform-ai/generations");
        assertThat(request.getBody().readUtf8())
                .contains("\"userId\":7", "\"offeringKey\":\"platform-codex-cli\"");
    }

    @Test
    void runtimeFailureMapsToSafePlatformUnavailableWithoutSecondRequest() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("private stderr"));
        assertThatThrownBy(() -> client().listOfferings(7L))
                .isInstanceOf(CodexAppException.class)
                .hasMessageNotContaining("private");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    private AiRuntimeCoreClient client() {
        return new AiRuntimeCoreClient(new AiRuntimeCoreProperties(
                server.url("/").toString(), "core-token", 1_000, 1_000, 2_000));
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body);
    }
}
