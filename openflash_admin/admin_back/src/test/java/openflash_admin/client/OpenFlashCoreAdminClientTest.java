package openflash_admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.config.OpenFlashCoreAdminProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenFlashCoreAdminClientTest {

    private static final String TOKEN = "core-admin-token";
    private static final String CORE_ADMIN_HEADER = "X-OpenFlash-Admin-Token";
    private static final String RUNTIME_ADMIN_HEADER = "X-OpenFlash-Ai-Runtime-Admin-Token";
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
    void accountWritesUseOnlyCoreAdminPathsAndToken() throws Exception {
        server.enqueue(json(200, "{\"updated\":true}"));
        server.enqueue(json(200, "{\"deleted\":true}"));
        OpenFlashCoreAdminClient client = client(1_000);

        client.setUserBanned(7L, 8L, true);
        client.deleteUser(7L, 8L);

        RecordedRequest banned = assertRequest("PUT", "/api/internal/admin/users/8/banned");
        JsonNode bannedBody = JSON.readTree(banned.getBody().readUtf8());
        assertThat(bannedBody.propertyNames().stream().collect(Collectors.toSet()))
            .isEqualTo(Set.of("actorUserId", "banned"));
        assertThat(bannedBody.get("actorUserId").longValue()).isEqualTo(7L);
        assertThat(bannedBody.get("banned").booleanValue()).isTrue();

        assertRequest("DELETE", "/api/internal/admin/users/8?actorUserId=7");
    }

    @Test
    void coreClientNoLongerExposesTemporaryCliAccessWrite() {
        assertThat(OpenFlashCoreAdminClient.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("setUserCliAccess"));
    }

    @Test
    void coreBusinessErrorsAreMappedWithoutEchoingRemoteBody() {
        server.enqueue(json(404,
            "{\"code\":40401,\"message\":\"" + TOKEN + " private stderr\"}"));

        AdminException failure = org.junit.jupiter.api.Assertions.assertThrows(
            AdminException.class, () -> client(1_000).deleteUser(7L, 8L));

        assertThat(failure.getErrorCode()).isEqualTo(AdminErrorCode.USER_NOT_FOUND);
        assertThat(failure.toString()).doesNotContain(TOKEN, "private", "stderr");
    }

    @Test
    void unknownErrorsTimeoutAndInvalidIdsBecomeSafe50301() {
        server.enqueue(json(500, "{\"message\":\"" + TOKEN + " stderr\"}"));
        assertUnavailable(() -> client(1_000).setUserBanned(7L, 8L, true));

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"deleted\":true}")
            .setHeadersDelay(250, TimeUnit.MILLISECONDS));
        assertUnavailable(() -> client(50).deleteUser(7L, 8L));

        assertUnavailable(() -> client(1_000).deleteUser(0L, 8L));
    }

    private OpenFlashCoreAdminClient client(int timeoutMillis) {
        return new OpenFlashCoreAdminClient(new OpenFlashCoreAdminProperties(
            server.url("/").toString(), TOKEN, 1_000, timeoutMillis));
    }

    private RecordedRequest assertRequest(String method, String path) throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo(method);
        assertThat(request.getPath()).isEqualTo(path);
        assertThat(request.getHeader(CORE_ADMIN_HEADER)).isEqualTo(TOKEN);
        assertThat(request.getHeader(RUNTIME_ADMIN_HEADER)).isNull();
        return request;
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body);
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(AdminException.class)
            .satisfies(failure -> {
                AdminException adminFailure = (AdminException) failure;
                assertThat(adminFailure.getErrorCode())
                    .isEqualTo(AdminErrorCode.RUNTIME_UNAVAILABLE);
                assertThat(adminFailure.toString()).doesNotContain(TOKEN, "stderr");
            });
    }
}
