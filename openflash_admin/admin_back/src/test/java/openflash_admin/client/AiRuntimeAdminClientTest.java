package openflash_admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.config.AiRuntimeAdminProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRuntimeAdminClientTest {

    private static final String TOKEN = "runtime-admin-token";
    private static final String ADMIN_HEADER = "X-OpenFlash-Ai-Runtime-Admin-Token";
    private static final String CORE_HEADER = "X-OpenFlash-Ai-Runtime-Core-Token";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void listUsesRuntimeAdminPathTokenAndAllowlistedCliDto() throws Exception {
        server.enqueue(json(200, """
            [{"cliKey":"codex","connectionKey":"platform-codex",
              "offeringKey":"platform-codex-cli","runtimeStatus":"AVAILABLE",
              "account":"must-not-escape"}]
            """));

        List<AiRuntimeAdminClient.CliSnapshot> clis = client(1_000).listClis();

        assertRequest("GET", "/api/internal/admin/clis");
        assertThat(clis).containsExactly(new AiRuntimeAdminClient.CliSnapshot(
            "codex", "platform-codex", "platform-codex-cli", "AVAILABLE"));
        assertThat(clis.toString()).doesNotContain("must-not-escape", TOKEN);
        assertThat(componentNames(AiRuntimeAdminClient.CliSnapshot.class))
            .containsExactlyInAnyOrder(
                "cliKey", "connectionKey", "offeringKey", "runtimeStatus");
    }

    @Test
    void detailAndAccountActionsUseOnlyFixedCodexAdminPaths() throws Exception {
        server.enqueue(json(200, """
            {"cli":{"cliKey":"codex","connectionKey":"platform-codex",
              "offeringKey":"platform-codex-cli","runtimeStatus":"NOT_LOGGED_IN"},
             "login":{"state":"IDLE","verificationUrl":null,"userCode":null},
             "secret":"must-not-escape"}
            """));
        server.enqueue(json(200, """
            {"state":"PENDING","verificationUrl":"https://auth.example/device",
             "userCode":"ABCD-EFGH","token":"must-not-escape"}
            """));
        server.enqueue(json(200,
            "{\"state\":\"CANCELED\",\"verificationUrl\":null,\"userCode\":null}"));
        server.enqueue(json(200, "{\"loggedOut\":true,\"raw\":\"ignored\"}"));
        AiRuntimeAdminClient client = client(1_000);

        AiRuntimeAdminClient.CliAdminSnapshot detail = client.codexSnapshot();
        AiRuntimeAdminClient.LoginSnapshot started = client.startCodexLogin();
        AiRuntimeAdminClient.LoginSnapshot canceled = client.cancelCodexLogin();
        client.logoutCodexAccount();

        assertThat(detail.cli().runtimeStatus()).isEqualTo("NOT_LOGGED_IN");
        assertThat(detail.toString()).doesNotContain("must-not-escape");
        assertThat(started).isEqualTo(new AiRuntimeAdminClient.LoginSnapshot(
            "PENDING", "https://auth.example/device", "ABCD-EFGH"));
        assertThat(canceled.state()).isEqualTo("CANCELED");
        assertRequest("GET", "/api/internal/admin/clis/codex");
        assertRequest("POST", "/api/internal/admin/clis/codex/login");
        assertRequest("DELETE", "/api/internal/admin/clis/codex/login");
        assertRequest("DELETE", "/api/internal/admin/clis/codex/account");
        assertThat(componentNames(AiRuntimeAdminClient.CliAdminSnapshot.class))
            .containsExactlyInAnyOrder("cli", "login");
        assertThat(componentNames(AiRuntimeAdminClient.LoginSnapshot.class))
            .containsExactlyInAnyOrder("state", "verificationUrl", "userCode");
    }

    @Test
    void platformPageUsesSafeAllowlistedProjection() throws Exception {
        server.enqueue(json(200, """
            {"runtimeStatus":"AVAILABLE","secret":"must-not-escape",
             "connections":[{"connectionKey":"platform-api","source":"PLATFORM","kind":"API",
               "protocol":"ANTHROPIC","baseUrl":"https://api.example.test",
               "credentialsConfigured":true,"enabled":true,"sortOrder":3,
               "secretEnc":"must-not-escape",
               "offerings":[{"offeringKey":"platform-model","source":"PLATFORM","modelKey":"gpt-5.4",
                 "enabled":true,"defaultAccess":false,"sortOrder":4,
                 "runtimeStatus":"AVAILABLE","internalId":99}]}]}
            """));

        AiRuntimeAdminClient.PlatformAiPageSnapshot page =
            client(1_000).platformAiPage();

        assertRequest("GET", "/api/internal/admin/platform-ai");
        assertThat(page).isEqualTo(new AiRuntimeAdminClient.PlatformAiPageSnapshot(
            "AVAILABLE",
            List.of(new AiRuntimeAdminClient.ConnectionSnapshot(
                "platform-api", "PLATFORM", "API", "ANTHROPIC", "https://api.example.test",
                true, true, 3,
                List.of(new AiRuntimeAdminClient.OfferingSnapshot(
                    "platform-model", "PLATFORM", "gpt-5.4", true, false, 4,
                    "AVAILABLE"))))));
        assertThat(page.toString()).doesNotContain(
            "must-not-escape", "secretEnc", "internalId", TOKEN);
        assertThat(componentNames(AiRuntimeAdminClient.PlatformAiPageSnapshot.class))
            .containsExactlyInAnyOrder("runtimeStatus", "connections");
        assertThat(componentNames(AiRuntimeAdminClient.ConnectionSnapshot.class))
            .containsExactlyInAnyOrder(
                "connectionKey", "source", "kind", "protocol", "displayName", "baseUrl",
                "credentialsConfigured", "enabled", "sortOrder", "offerings");
        assertThat(componentNames(AiRuntimeAdminClient.OfferingSnapshot.class))
            .containsExactlyInAnyOrder(
                "offeringKey", "source", "modelKey", "enabled", "defaultAccess",
                "sortOrder", "runtimeStatus");
    }

    @Test
    void platformPageRejectsMissingOrNonPlatformSource() {
        server.enqueue(json(200, """
            {"runtimeStatus":"AVAILABLE","connections":[{
              "connectionKey":"platform-api","kind":"API","protocol":"ANTHROPIC",
              "baseUrl":null,"credentialsConfigured":false,"enabled":true,
              "sortOrder":0,"offerings":[]}]}
            """));
        assertError(AdminErrorCode.GENERIC_ERROR, () -> client(1_000).platformAiPage());

        server.enqueue(json(200, """
            {"runtimeStatus":"AVAILABLE","connections":[{
              "connectionKey":"platform-api","source":"USER","kind":"API",
              "protocol":"ANTHROPIC","baseUrl":null,"credentialsConfigured":false,
              "enabled":true,"sortOrder":0,"offerings":[]}]}
            """));
        assertError(AdminErrorCode.GENERIC_ERROR, () -> client(1_000).platformAiPage());
    }

    @Test
    void everyPlatformMutationUsesExactRuntimePathVerbTokenAndPayload() throws Exception {
        server.enqueue(json(200, connectionJson()));
        server.enqueue(json(200, connectionJson()));
        server.enqueue(json(200, ""));
        server.enqueue(json(200, ""));
        server.enqueue(json(200, "[{\"modelKey\":\"gpt-5.4\"}]"));
        server.enqueue(json(200, offeringJson()));
        server.enqueue(json(200, offeringJson()));
        server.enqueue(json(200, ""));
        server.enqueue(json(200, ""));
        server.enqueue(json(200, ""));
        server.enqueue(json(200, ""));
        AiRuntimeAdminClient client = client(1_000);

        client.createConnection(new AiRuntimeAdminClient.CreateConnectionRequest(
            "API", "ANTHROPIC", null, "https://api.example.test", 3));
        client.updateConnection("platform-api", new AiRuntimeAdminClient.UpdateConnectionRequest(
            "https://api.example.test", false, 5));
        client.replaceCredentials("platform-api",
            new AiRuntimeAdminClient.ReplaceCredentialsRequest("plain-secret"));
        client.deleteConnection("platform-api");
        assertThat(client.discoverModels("platform-api"))
            .containsExactly(new AiRuntimeAdminClient.DiscoveredModel("gpt-5.4"));
        client.createOffering("platform-api", new AiRuntimeAdminClient.CreateOfferingRequest(
            "gpt-5.4", 4));
        client.updateOffering("platform-model", new AiRuntimeAdminClient.UpdateOfferingRequest(
            "gpt-5.4", false, 6));
        client.deleteOffering("platform-model");
        client.setDefaultAccess("platform-model",
            new AiRuntimeAdminClient.SetDefaultAccessRequest(true));
        client.setUserAccess("platform-model", 8L,
            new AiRuntimeAdminClient.SetUserAccessRequest(false));
        client.deleteUserAccess("platform-model", 8L);

        assertJsonRequest("POST", "/api/internal/admin/platform-ai/connections",
            Set.of("kind", "protocol", "cliKey", "displayName", "baseUrl", "sortOrder"),
            "plain-secret");
        assertJsonRequest("PUT", "/api/internal/admin/platform-ai/connections/platform-api",
            Set.of("baseUrl", "enabled", "sortOrder"), "plain-secret");
        RecordedRequest credential = assertRequest(
            "PUT", "/api/internal/admin/platform-ai/connections/platform-api/credentials");
        JsonNode credentialBody = JSON.readTree(credential.getBody().readUtf8());
        assertThat(credentialBody.propertyNames().stream().collect(Collectors.toSet()))
            .isEqualTo(Set.of("apiKey"));
        assertThat(credentialBody.get("apiKey").asString())
            .isEqualTo("plain-secret");
        assertRequest("DELETE", "/api/internal/admin/platform-ai/connections/platform-api");
        assertRequest("POST",
            "/api/internal/admin/platform-ai/connections/platform-api/models/discover");
        assertJsonRequest("POST",
            "/api/internal/admin/platform-ai/connections/platform-api/offerings",
            Set.of("modelKey", "sortOrder"), "plain-secret");
        assertJsonRequest("PUT", "/api/internal/admin/platform-ai/offerings/platform-model",
            Set.of("modelKey", "enabled", "sortOrder"), "plain-secret");
        assertRequest("DELETE", "/api/internal/admin/platform-ai/offerings/platform-model");
        assertJsonRequest("PUT",
            "/api/internal/admin/platform-ai/offerings/platform-model/access/default",
            Set.of("enabled"), "plain-secret");
        assertJsonRequest("PUT",
            "/api/internal/admin/platform-ai/offerings/platform-model/access/users/8",
            Set.of("enabled"), "plain-secret");
        assertRequest("DELETE",
            "/api/internal/admin/platform-ai/offerings/platform-model/access/users/8");
    }

    @Test
    void onlyConnectivityTimeoutAndRuntime503BecomeDegradedUnavailable() {
        server.enqueue(json(503, "{\"code\":50301}"));
        assertError(AdminErrorCode.RUNTIME_UNAVAILABLE, () -> client(1_000).listClis());

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("[]")
            .setHeadersDelay(250, TimeUnit.MILLISECONDS));
        assertError(AdminErrorCode.RUNTIME_UNAVAILABLE, () -> client(50).listClis());

        AiRuntimeAdminClient blank = new AiRuntimeAdminClient(
            new AiRuntimeAdminProperties(server.url("/").toString(), " ", 100, 100));
        assertError(AdminErrorCode.RUNTIME_UNAVAILABLE, blank::listClis);
    }

    @Test
    void runtimeAuthValidationNotFoundAndProgrammingErrorsAreNotMisclassifiedOffline() {
        server.enqueue(json(403, "{\"code\":40301,\"message\":\"" + TOKEN + "\"}"));
        assertError(AdminErrorCode.FORBIDDEN, () -> client(1_000).platformAiPage());

        server.enqueue(json(400, "{\"code\":40001}"));
        assertError(AdminErrorCode.INVALID_REQUEST, () -> client(1_000).platformAiPage());

        server.enqueue(json(404, "{\"code\":40401}"));
        assertError(AdminErrorCode.PLATFORM_AI_NOT_FOUND,
            () -> client(1_000).platformAiPage());

        server.enqueue(json(500, "{\"code\":50000,\"message\":\"stderr\"}"));
        assertError(AdminErrorCode.GENERIC_ERROR, () -> client(1_000).platformAiPage());

        server.enqueue(json(200, "{\"runtimeStatus\":5,\"connections\":[]}"));
        assertError(AdminErrorCode.GENERIC_ERROR, () -> client(1_000).platformAiPage());
    }

    @ParameterizedTest
    @MethodSource("matchingRuntimeErrors")
    void mapsRuntimeErrorOnlyWhenHttpStatusAndBodyCodeMatch(
            int status,
            int code,
            AdminErrorCode expected,
            boolean unavailable) {
        server.enqueue(json(status, "{\"code\":" + code + "}"));

        assertMappedError(expected, unavailable, () -> client(1_000).platformAiPage());
    }

    @ParameterizedTest
    @MethodSource("nonUnavailableHttpStatuses")
    void doesNotTreat50301AsOfflineWithoutHttp503(int status) {
        server.enqueue(json(status,
            "{\"code\":50301,\"message\":\"" + TOKEN + " response-secret\"}"));

        assertMappedError(
            AdminErrorCode.GENERIC_ERROR, false, () -> client(1_000).platformAiPage());
    }

    @ParameterizedTest
    @MethodSource("nonUnavailableRuntimeCodes")
    void doesNotTreatHttp503AsOfflineWithoutCode50301(int code) {
        server.enqueue(json(503,
            "{\"code\":" + code + ",\"message\":\"" + TOKEN
                + " response-secret\"}"));

        assertMappedError(
            AdminErrorCode.GENERIC_ERROR, false, () -> client(1_000).platformAiPage());
    }

    @ParameterizedTest
    @MethodSource("malformedRuntimeErrors")
    void malformedOrMissingRuntimeCodeIsSafeProtocolFailure(String body) {
        server.enqueue(json(503, body));

        assertMappedError(
            AdminErrorCode.GENERIC_ERROR, false, () -> client(1_000).platformAiPage());
    }

    private static Stream<Arguments> matchingRuntimeErrors() {
        return Stream.of(
            Arguments.of(400, 40001, AdminErrorCode.INVALID_REQUEST, false),
            Arguments.of(403, 40301, AdminErrorCode.FORBIDDEN, false),
            Arguments.of(404, 40401, AdminErrorCode.PLATFORM_AI_NOT_FOUND, false),
            Arguments.of(500, 50000, AdminErrorCode.GENERIC_ERROR, false),
            Arguments.of(503, 50301, AdminErrorCode.RUNTIME_UNAVAILABLE, true));
    }

    private static Stream<Integer> nonUnavailableHttpStatuses() {
        return Stream.of(400, 401, 403, 404, 500);
    }

    private static Stream<Integer> nonUnavailableRuntimeCodes() {
        return Stream.of(40001, 40301, 40401, 50000);
    }

    private static Stream<String> malformedRuntimeErrors() {
        return Stream.of(
            "{}",
            "{\"code\":\"50301\"}",
            "{\"code\":null}",
            "{\"code\":4295017597}",
            "[]",
            "not-json");
    }

    private AiRuntimeAdminClient client(int timeoutMillis) {
        return new AiRuntimeAdminClient(new AiRuntimeAdminProperties(
            server.url("/").toString(), TOKEN, 1_000, timeoutMillis));
    }

    private RecordedRequest assertRequest(String method, String path) throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo(method);
        assertThat(request.getPath()).isEqualTo(path);
        assertThat(request.getHeader(ADMIN_HEADER)).isEqualTo(TOKEN);
        assertThat(request.getHeader(CORE_HEADER)).isNull();
        return request;
    }

    private RecordedRequest assertJsonRequest(
            String method,
            String path,
            Set<String> fields,
            String forbiddenValue) throws Exception {
        RecordedRequest request = assertRequest(method, path);
        JsonNode body = JSON.readTree(request.getBody().readUtf8());
        assertThat(body).isNotNull();
        assertThat(body.propertyNames().stream().collect(Collectors.toSet()))
            .isEqualTo(fields);
        if (forbiddenValue != null) {
            assertThat(body.toString()).doesNotContain(forbiddenValue);
        }
        return request;
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body);
    }

    private static String connectionJson() {
        return """
            {"connectionKey":"platform-api","source":"PLATFORM","kind":"API","protocol":"ANTHROPIC",
             "baseUrl":"https://api.example.test","credentialsConfigured":true,
             "enabled":true,"sortOrder":3,"offerings":[]}
            """;
    }

    private static String offeringJson() {
        return """
            {"offeringKey":"platform-model","source":"PLATFORM","modelKey":"gpt-5.4","enabled":true,
             "defaultAccess":false,"sortOrder":4,"runtimeStatus":"AVAILABLE"}
            """;
    }

    private static List<String> componentNames(Class<?> recordType) {
        return List.of(recordType.getRecordComponents()).stream()
            .map(component -> component.getName())
            .toList();
    }

    private static void assertError(AdminErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(AdminException.class)
            .satisfies(failure -> {
                AdminException adminFailure = (AdminException) failure;
                assertThat(adminFailure.getErrorCode()).isEqualTo(expected);
                assertThat(adminFailure.toString()).doesNotContain(
                    TOKEN, "CODEX_HOME", "stderr", "plain-secret");
            });
    }

    private static void assertMappedError(
            AdminErrorCode expected,
            boolean unavailable,
            Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(AdminException.class)
            .satisfies(failure -> {
                AdminException adminFailure = (AdminException) failure;
                assertThat(adminFailure.getErrorCode()).isEqualTo(expected);
                assertThat(failure instanceof AdminRuntimeUnavailableException)
                    .isEqualTo(unavailable);
                assertThat(adminFailure.toString()).doesNotContain(
                    TOKEN, "response-secret", "CODEX_HOME", "stderr", "plain-secret");
            });
    }
}
