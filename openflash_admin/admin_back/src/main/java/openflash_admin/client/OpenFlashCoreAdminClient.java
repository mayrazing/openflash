package openflash_admin.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.config.OpenFlashCoreAdminProperties;
import tools.jackson.databind.JsonNode;

/** 只调用 openflash_back 拥有的账号封禁和业务级联删除 API. */
@Component
public class OpenFlashCoreAdminClient {

    private static final String TOKEN_HEADER = "X-OpenFlash-Admin-Token";
    private final OpenFlashCoreAdminProperties properties;
    private final RestClient restClient;

    public OpenFlashCoreAdminClient(OpenFlashCoreAdminProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(duration(properties.getConnectTimeoutMillis()));
        requestFactory.setReadTimeout(duration(properties.getReadTimeoutMillis()));
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().set(TOKEN_HEADER, requireToken());
                return execution.execute(request, body);
            })
            .build();
    }

    public void setUserBanned(Long actorUserId, Long userId, boolean banned) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actorUserId", requireId(actorUserId));
        payload.put("banned", banned);
        JsonNode body = requestWithBody(
            HttpMethod.PUT,
            "/api/internal/admin/users/" + requireId(userId) + "/banned",
            payload);
        requireTrue(body, "updated");
    }

    public void deleteUser(Long actorUserId, Long userId) {
        JsonNode body = request(
            HttpMethod.DELETE,
            "/api/internal/admin/users/" + requireId(userId)
                + "?actorUserId=" + requireId(actorUserId));
        requireTrue(body, "deleted");
    }

    private JsonNode request(HttpMethod method, String path) {
        try {
            JsonNode body = restClient.method(method).uri(path).retrieve().body(JsonNode.class);
            if (body == null || !body.isObject()) throw unavailable();
            return body;
        } catch (AdminException failure) {
            throw failure;
        } catch (RestClientResponseException failure) {
            throw mapCoreError(failure);
        } catch (RestClientException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private JsonNode requestWithBody(HttpMethod method, String path, Object payload) {
        try {
            JsonNode body = restClient.method(method)
                .uri(path)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
            if (body == null || !body.isObject()) throw unavailable();
            return body;
        } catch (AdminException failure) {
            throw failure;
        } catch (RestClientResponseException failure) {
            throw mapCoreError(failure);
        } catch (RestClientException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private AdminException mapCoreError(RestClientResponseException failure) {
        try {
            JsonNode body = failure.getResponseBodyAs(JsonNode.class);
            JsonNode code = body == null || !body.isObject() ? null : body.get("code");
            if (code == null || !code.isIntegralNumber()) return unavailable();
            return switch (code.intValue()) {
                case 40401 -> new AdminException(AdminErrorCode.USER_NOT_FOUND);
                case 40901 -> new AdminException(AdminErrorCode.LAST_ADMIN_REQUIRED);
                case 40902 -> new AdminException(AdminErrorCode.SELF_ACCOUNT_MUTATION);
                default -> unavailable();
            };
        } catch (RuntimeException ignored) {
            return unavailable();
        }
    }

    private static long requireId(Long id) {
        if (id == null || id <= 0) throw unavailable();
        return id;
    }

    private static void requireTrue(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isBoolean() || !value.booleanValue()) throw unavailable();
    }

    private String requireToken() {
        String token = properties.getToken();
        if (token == null || token.isBlank()) throw unavailable();
        return token;
    }

    private static Duration duration(int millis) {
        if (millis <= 0) throw unavailable();
        return Duration.ofMillis(millis);
    }

    private static AdminException unavailable() {
        return new AdminException(AdminErrorCode.RUNTIME_UNAVAILABLE);
    }
}
