package openflash_admin.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.config.AiRuntimeAdminProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** 只调用 ai_runtime 的 admin-scope 安全 API. */
@Component
public class AiRuntimeAdminClient {

    private static final String ADMIN_HEADER = "X-OpenFlash-Ai-Runtime-Admin-Token";
    private static final String CLIS_PATH = "/api/internal/admin/clis";
    private static final String PLATFORM_PATH = "/api/internal/admin/platform-ai";
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");
    private static final Set<String> CLI_RUNTIME_STATUSES = Set.of(
        "AVAILABLE", "NOT_LOGGED_IN", "NOT_INSTALLED", "ERROR");
    private static final Set<String> PAGE_RUNTIME_STATUSES = Set.of(
        "AVAILABLE", "UNAVAILABLE");
    private static final Set<String> OFFERING_RUNTIME_STATUSES = Set.of(
        "AVAILABLE", "NOT_LOGGED_IN", "NOT_INSTALLED", "ERROR", "UNAVAILABLE");
    private static final Set<String> LOGIN_STATES = Set.of(
        "IDLE", "STARTING", "PENDING", "SUCCEEDED", "FAILED", "EXPIRED", "CANCELED");

    private final AiRuntimeAdminProperties properties;
    private final RestClient restClient;

    public AiRuntimeAdminClient(AiRuntimeAdminProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(duration(properties.getConnectTimeoutMillis()));
        requestFactory.setReadTimeout(duration(properties.getReadTimeoutMillis()));
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().set(ADMIN_HEADER, requireToken());
                return execution.execute(request, body);
            })
            .build();
    }

    public List<CliSnapshot> listClis() {
        JsonNode body = requestNode(HttpMethod.GET, CLIS_PATH, null);
        if (!body.isArray()) throw protocolFailure();
        List<CliSnapshot> result = new ArrayList<>();
        for (JsonNode item : body) {
            result.add(cli(item));
        }
        return List.copyOf(result);
    }

    public CliAdminSnapshot codexSnapshot() {
        JsonNode body = requestObject(HttpMethod.GET, CLIS_PATH + "/codex", null);
        return new CliAdminSnapshot(cli(body.get("cli")), login(body.get("login")));
    }

    public LoginSnapshot startCodexLogin() {
        return login(requestObject(HttpMethod.POST, CLIS_PATH + "/codex/login", null));
    }

    public LoginSnapshot cancelCodexLogin() {
        return login(requestObject(HttpMethod.DELETE, CLIS_PATH + "/codex/login", null));
    }

    public void logoutCodexAccount() {
        JsonNode body = requestObject(HttpMethod.DELETE, CLIS_PATH + "/codex/account", null);
        if (!requiredBoolean(body, "loggedOut")) throw protocolFailure();
    }

    public PlatformAiPageSnapshot platformAiPage() {
        JsonNode body = requestObject(HttpMethod.GET, PLATFORM_PATH, null);
        String runtimeStatus = requiredEnumText(
            body, "runtimeStatus", PAGE_RUNTIME_STATUSES);
        JsonNode connections = body.get("connections");
        if (connections == null || !connections.isArray()) throw protocolFailure();
        List<ConnectionSnapshot> result = new ArrayList<>();
        for (JsonNode connection : connections) {
            result.add(connection(connection));
        }
        return new PlatformAiPageSnapshot(runtimeStatus, result);
    }

    public ConnectionSnapshot createConnection(CreateConnectionRequest request) {
        return connection(requestObject(
            HttpMethod.POST, PLATFORM_PATH + "/connections", requireRequest(request)));
    }

    public ConnectionSnapshot updateConnection(
            String connectionKey,
            UpdateConnectionRequest request) {
        return connection(requestObject(
            HttpMethod.PUT,
            PLATFORM_PATH + "/connections/" + requireKey(connectionKey),
            requireRequest(request)));
    }

    public void replaceCredentials(
            String connectionKey,
            ReplaceCredentialsRequest request) {
        requestVoid(
            HttpMethod.PUT,
            PLATFORM_PATH + "/connections/" + requireKey(connectionKey) + "/credentials",
            requireRequest(request));
    }

    public void deleteConnection(String connectionKey) {
        requestVoid(
            HttpMethod.DELETE,
            PLATFORM_PATH + "/connections/" + requireKey(connectionKey),
            null);
    }

    public List<DiscoveredModel> discoverModels(String connectionKey) {
        JsonNode body = requestNode(
            HttpMethod.POST,
            PLATFORM_PATH + "/connections/" + requireKey(connectionKey) + "/models/discover",
            null);
        if (!body.isArray()) throw protocolFailure();
        List<DiscoveredModel> result = new ArrayList<>();
        for (JsonNode item : body) {
            if (item == null || !item.isObject()) throw protocolFailure();
            result.add(new DiscoveredModel(requiredText(item, "modelKey")));
        }
        return List.copyOf(result);
    }

    public List<DiscoveredModel> discoverModels(DiscoverModelsRequest request) {
        JsonNode body = requestNode(
            HttpMethod.POST, PLATFORM_PATH + "/models/discover", requireRequest(request));
        if (!body.isArray()) throw protocolFailure();
        List<DiscoveredModel> result = new ArrayList<>();
        for (JsonNode item : body) {
            if (item == null || !item.isObject()) throw protocolFailure();
            result.add(new DiscoveredModel(requiredText(item, "modelKey")));
        }
        return List.copyOf(result);
    }

    public OfferingSnapshot createOffering(
            String connectionKey,
            CreateOfferingRequest request) {
        return offering(requestObject(
            HttpMethod.POST,
            PLATFORM_PATH + "/connections/" + requireKey(connectionKey) + "/offerings",
            requireRequest(request)));
    }

    public OfferingSnapshot updateOffering(
            String offeringKey,
            UpdateOfferingRequest request) {
        return offering(requestObject(
            HttpMethod.PUT,
            PLATFORM_PATH + "/offerings/" + requireKey(offeringKey),
            requireRequest(request)));
    }

    public void deleteOffering(String offeringKey) {
        requestVoid(
            HttpMethod.DELETE,
            PLATFORM_PATH + "/offerings/" + requireKey(offeringKey),
            null);
    }

    public void setDefaultAccess(
            String offeringKey,
            SetDefaultAccessRequest request) {
        requestVoid(
            HttpMethod.PUT,
            PLATFORM_PATH + "/offerings/" + requireKey(offeringKey) + "/access/default",
            requireRequest(request));
    }

    public void setUserAccess(
            String offeringKey,
            long userId,
            SetUserAccessRequest request) {
        requestVoid(
            HttpMethod.PUT,
            PLATFORM_PATH + "/offerings/" + requireKey(offeringKey)
                + "/access/users/" + requireUserId(userId),
            requireRequest(request));
    }

    public void deleteUserAccess(String offeringKey, long userId) {
        requestVoid(
            HttpMethod.DELETE,
            PLATFORM_PATH + "/offerings/" + requireKey(offeringKey)
                + "/access/users/" + requireUserId(userId),
            null);
    }

    private JsonNode requestObject(HttpMethod method, String path, Object payload) {
        JsonNode body = requestNode(method, path, payload);
        if (!body.isObject()) throw protocolFailure();
        return body;
    }

    private JsonNode requestNode(HttpMethod method, String path, Object payload) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(path);
            if (payload != null) request.body(payload);
            JsonNode body = request.retrieve().body(JsonNode.class);
            if (body == null) throw protocolFailure();
            return body;
        } catch (AdminException failure) {
            throw failure;
        } catch (RestClientResponseException failure) {
            throw mapRuntimeError(failure);
        } catch (ResourceAccessException failure) {
            throw unavailable();
        } catch (RestClientException | IllegalArgumentException failure) {
            throw protocolFailure();
        }
    }

    private void requestVoid(HttpMethod method, String path, Object payload) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(path);
            if (payload != null) request.body(payload);
            request.retrieve().toBodilessEntity();
        } catch (AdminException failure) {
            throw failure;
        } catch (RestClientResponseException failure) {
            throw mapRuntimeError(failure);
        } catch (ResourceAccessException failure) {
            throw unavailable();
        } catch (RestClientException | IllegalArgumentException failure) {
            throw protocolFailure();
        }
    }

    private AdminException mapRuntimeError(RestClientResponseException failure) {
        int status = failure.getStatusCode().value();
        int code = safeErrorCode(failure);
        if (status == 400 && code == 40001) {
            return new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        if (status == 403 && code == 40301) {
            return new AdminException(AdminErrorCode.FORBIDDEN);
        }
        if (status == 404 && code == 40401) {
            return new AdminException(AdminErrorCode.PLATFORM_AI_NOT_FOUND);
        }
        if (status == 503 && code == 50301) return unavailable();
        return protocolFailure();
    }

    private int safeErrorCode(RestClientResponseException failure) {
        try {
            JsonNode body = failure.getResponseBodyAs(JsonNode.class);
            JsonNode code = body == null || !body.isObject() ? null : body.get("code");
            return code != null && code.isIntegralNumber() ? code.intValue() : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private ConnectionSnapshot connection(JsonNode value) {
        if (value == null || !value.isObject()) throw protocolFailure();
        String source = requiredEnumText(value, "source", Set.of("PLATFORM"));
        String kind = requiredEnumText(value, "kind", Set.of("API", "CLI"));
        String protocol = requiredEnumText(
            value, "protocol", Set.of("ANTHROPIC", "CODEX_APP_SERVER"));
        JsonNode offerings = value.get("offerings");
        if (offerings == null || !offerings.isArray()) throw protocolFailure();
        List<OfferingSnapshot> children = new ArrayList<>();
        for (JsonNode child : offerings) {
            children.add(offering(child));
        }
        return new ConnectionSnapshot(
            requiredKey(value, "connectionKey"),
            source,
            kind,
            protocol,
            optionalText(value, "displayName"),
            optionalText(value, "baseUrl"),
            requiredBoolean(value, "credentialsConfigured"),
            requiredBoolean(value, "enabled"),
            requiredInt(value, "sortOrder"),
            children);
    }

    private OfferingSnapshot offering(JsonNode value) {
        if (value == null || !value.isObject()) throw protocolFailure();
        return new OfferingSnapshot(
            requiredKey(value, "offeringKey"),
            requiredEnumText(value, "source", Set.of("PLATFORM")),
            optionalText(value, "modelKey"),
            requiredBoolean(value, "enabled"),
            requiredBoolean(value, "defaultAccess"),
            requiredInt(value, "sortOrder"),
            requiredEnumText(value, "runtimeStatus", OFFERING_RUNTIME_STATUSES));
    }

    private CliSnapshot cli(JsonNode value) {
        if (value == null || !value.isObject()) throw protocolFailure();
        return new CliSnapshot(
            requiredKey(value, "cliKey"),
            requiredKey(value, "connectionKey"),
            requiredKey(value, "offeringKey"),
            requiredEnumText(value, "runtimeStatus", CLI_RUNTIME_STATUSES));
    }

    private LoginSnapshot login(JsonNode value) {
        if (value == null || !value.isObject()) throw protocolFailure();
        return new LoginSnapshot(
            requiredEnumText(value, "state", LOGIN_STATES),
            optionalText(value, "verificationUrl"),
            optionalText(value, "userCode"));
    }

    private String requiredKey(JsonNode object, String field) {
        return requireKey(requiredText(object, field));
    }

    private String requiredEnumText(JsonNode object, String field, Set<String> allowed) {
        String value = requiredText(object, field);
        if (!allowed.contains(value)) throw protocolFailure();
        return value;
    }

    private String requiredText(JsonNode object, String field) {
        String value = optionalText(object, field);
        if (value == null || value.isBlank()) throw protocolFailure();
        return value;
    }

    private String optionalText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw protocolFailure();
        String text = value.asString();
        rejectTokenEcho(text);
        return text;
    }

    private boolean requiredBoolean(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isBoolean()) throw protocolFailure();
        return value.booleanValue();
    }

    private int requiredInt(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isInt()) throw protocolFailure();
        return value.intValue();
    }

    private String requireKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        return key;
    }

    private long requireUserId(long userId) {
        if (userId <= 0L) throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        return userId;
    }

    private <T> T requireRequest(T request) {
        if (request == null) throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        return request;
    }

    private String requireToken() {
        String token = properties.getAdminToken();
        if (token == null || token.isBlank()) throw unavailable();
        return token;
    }

    private void rejectTokenEcho(String value) {
        String token = properties.getAdminToken();
        if (token == null || token.isBlank() || value.contains(token)) {
            throw protocolFailure();
        }
    }

    private static Duration duration(int millis) {
        if (millis <= 0) throw unavailable();
        return Duration.ofMillis(millis);
    }

    private static AdminRuntimeUnavailableException unavailable() {
        return new AdminRuntimeUnavailableException();
    }

    private static AdminException protocolFailure() {
        return new AdminException(AdminErrorCode.GENERIC_ERROR);
    }

    public static final class AdminRuntimeUnavailableException extends AdminException {
        public AdminRuntimeUnavailableException() {
            super(AdminErrorCode.RUNTIME_UNAVAILABLE);
        }
    }

    public record CliSnapshot(
            String cliKey,
            String connectionKey,
            String offeringKey,
            String runtimeStatus) {
    }

    public record CliAdminSnapshot(CliSnapshot cli, LoginSnapshot login) {
    }

    public record LoginSnapshot(String state, String verificationUrl, String userCode) {
    }

    public record PlatformAiPageSnapshot(
            String runtimeStatus,
            List<ConnectionSnapshot> connections) {
        public PlatformAiPageSnapshot {
            connections = List.copyOf(connections);
        }
    }

    public record ConnectionSnapshot(
            String connectionKey,
            String source,
            String kind,
            String protocol,
            String displayName,
            String baseUrl,
            boolean credentialsConfigured,
            boolean enabled,
            int sortOrder,
            List<OfferingSnapshot> offerings) {
        public ConnectionSnapshot {
            offerings = List.copyOf(offerings);
        }
        public ConnectionSnapshot(
                String connectionKey, String source, String kind, String protocol,
                String baseUrl, boolean credentialsConfigured, boolean enabled,
                int sortOrder, List<OfferingSnapshot> offerings) {
            this(connectionKey, source, kind, protocol, null, baseUrl,
                    credentialsConfigured, enabled, sortOrder, offerings);
        }
    }

    public record OfferingSnapshot(
            String offeringKey,
            String source,
            String modelKey,
            boolean enabled,
            boolean defaultAccess,
            int sortOrder,
            String runtimeStatus) {
    }

    public record CreateConnectionRequest(
            String kind,
            String protocol,
            String cliKey,
            String displayName,
            String baseUrl,
            int sortOrder) {
        public CreateConnectionRequest(
                String kind, String protocol, String cliKey, String baseUrl, int sortOrder) {
            this(kind, protocol, cliKey, null, baseUrl, sortOrder);
        }
    }

    public record DiscoverModelsRequest(String baseUrl, String apiKey) {
        @Override
        public String toString() {
            return "DiscoverModelsRequest[baseUrl=" + baseUrl + ", apiKey=<redacted>]";
        }
    }

    public record UpdateConnectionRequest(String baseUrl, boolean enabled, int sortOrder) {
    }

    public record ReplaceCredentialsRequest(String apiKey) {
        @Override
        public String toString() {
            return "ReplaceCredentialsRequest[apiKey=<redacted>]";
        }
    }

    public record CreateOfferingRequest(String modelKey, int sortOrder) {
    }

    public record UpdateOfferingRequest(String modelKey, boolean enabled, int sortOrder) {
    }

    public record SetDefaultAccessRequest(boolean enabled) {
    }

    public record SetUserAccessRequest(boolean enabled) {
    }

    public record DiscoveredModel(String modelKey) {
    }
}
