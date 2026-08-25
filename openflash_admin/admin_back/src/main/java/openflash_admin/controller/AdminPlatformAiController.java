package openflash_admin.controller;

import java.util.List;
import java.util.Set;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.common.ApiResponse;
import openflash_admin.client.AiRuntimeAdminClient.ConnectionSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.CreateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.CreateOfferingRequest;
import openflash_admin.client.AiRuntimeAdminClient.DiscoveredModel;
import openflash_admin.client.AiRuntimeAdminClient.DiscoverModelsRequest;
import openflash_admin.client.AiRuntimeAdminClient.OfferingSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.ReplaceCredentialsRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetDefaultAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateOfferingRequest;
import openflash_admin.dto.PlatformAiPageResponse;
import openflash_admin.service.AdminPlatformAiService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 对浏览器公开平台 AI 管理 API, 响应只包含安全 DTO. */
@RestController
@RequestMapping("/api/admin/platform-ai")
public class AdminPlatformAiController {

    private final AdminPlatformAiService service;

    public AdminPlatformAiController(AdminPlatformAiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PlatformAiPageResponse> page() {
        return ApiResponse.success(service.page());
    }

    @PostMapping("/connections")
    public ApiResponse<ConnectionSnapshot> createConnection(@RequestBody JsonNode body) {
        requireFields(body, Set.of("kind", "protocol", "cliKey", "baseUrl", "sortOrder"),
            Set.of("displayName"));
        return ApiResponse.success(service.createConnection(new CreateConnectionRequest(
            requiredText(body, "kind"),
            requiredText(body, "protocol"),
            nullableText(body, "cliKey"),
            nullableText(body, "displayName"),
            nullableText(body, "baseUrl"),
            requiredInt(body, "sortOrder"))));
    }

    @PutMapping("/connections/{connectionKey}")
    public ApiResponse<ConnectionSnapshot> updateConnection(
            @PathVariable String connectionKey,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("baseUrl", "enabled", "sortOrder"));
        return ApiResponse.success(service.updateConnection(
            connectionKey,
            new UpdateConnectionRequest(
                nullableText(body, "baseUrl"),
                requiredBoolean(body, "enabled"),
                requiredInt(body, "sortOrder"))));
    }

    @PutMapping("/connections/{connectionKey}/credentials")
    public ApiResponse<Void> replaceCredentials(
            @PathVariable String connectionKey,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("apiKey"));
        service.replaceCredentials(
            connectionKey, new ReplaceCredentialsRequest(requiredText(body, "apiKey")));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/connections/{connectionKey}")
    public ApiResponse<Void> deleteConnection(@PathVariable String connectionKey) {
        service.deleteConnection(connectionKey);
        return ApiResponse.success(null);
    }

    @PostMapping("/connections/{connectionKey}/models/discover")
    public ApiResponse<List<DiscoveredModel>> discoverModels(
            @PathVariable String connectionKey) {
        return ApiResponse.success(service.discoverModels(connectionKey));
    }

    @PostMapping("/models/discover")
    public ApiResponse<List<DiscoveredModel>> discoverModels(@RequestBody JsonNode body) {
        requireFields(body, Set.of("baseUrl", "apiKey"));
        return ApiResponse.success(service.discoverModels(new DiscoverModelsRequest(
            requiredText(body, "baseUrl"), requiredText(body, "apiKey"))));
    }

    @PostMapping("/connections/{connectionKey}/offerings")
    public ApiResponse<OfferingSnapshot> createOffering(
            @PathVariable String connectionKey,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("modelKey", "sortOrder"));
        return ApiResponse.success(service.createOffering(
            connectionKey,
            new CreateOfferingRequest(
                requiredText(body, "modelKey"), requiredInt(body, "sortOrder"))));
    }

    @PutMapping("/offerings/{offeringKey}")
    public ApiResponse<OfferingSnapshot> updateOffering(
            @PathVariable String offeringKey,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("modelKey", "enabled", "sortOrder"));
        return ApiResponse.success(service.updateOffering(
            offeringKey,
            new UpdateOfferingRequest(
                nullableText(body, "modelKey"),
                requiredBoolean(body, "enabled"),
                requiredInt(body, "sortOrder"))));
    }

    @DeleteMapping("/offerings/{offeringKey}")
    public ApiResponse<Void> deleteOffering(@PathVariable String offeringKey) {
        service.deleteOffering(offeringKey);
        return ApiResponse.success(null);
    }

    @PutMapping("/offerings/{offeringKey}/access/default")
    public ApiResponse<Void> setDefaultAccess(
            @PathVariable String offeringKey,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("enabled"));
        service.setDefaultAccess(
            offeringKey, new SetDefaultAccessRequest(requiredBoolean(body, "enabled")));
        return ApiResponse.success(null);
    }

    @PutMapping("/offerings/{offeringKey}/access/users/{userId}")
    public ApiResponse<Void> setUserAccess(
            @PathVariable String offeringKey,
            @PathVariable long userId,
            @RequestBody JsonNode body) {
        requireFields(body, Set.of("enabled"));
        service.setUserAccess(
            offeringKey, userId,
            new SetUserAccessRequest(requiredBoolean(body, "enabled")));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/offerings/{offeringKey}/access/users/{userId}")
    public ApiResponse<Void> deleteUserAccess(
            @PathVariable String offeringKey,
            @PathVariable long userId) {
        service.deleteUserAccess(offeringKey, userId);
        return ApiResponse.success(null);
    }

    private static void requireFields(JsonNode body, Set<String> expected) {
        if (body == null
                || !body.isObject()
                || body.size() != expected.size()
                || !expected.stream().allMatch(body::has)) {
            throw invalidRequest();
        }
    }

    private static void requireFields(
            JsonNode body, Set<String> expected, Set<String> optional) {
        if (body == null
                || !body.isObject()
                || !expected.stream().allMatch(body::has)
                || body.size() < expected.size()
                || body.size() > expected.size() + optional.size()) {
            throw invalidRequest();
        }
        for (String field : body.propertyNames()) {
            if (!expected.contains(field) && !optional.contains(field)) throw invalidRequest();
        }
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw invalidRequest();
        }
        return value.asString();
    }

    private static String nullableText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw invalidRequest();
        return value.asString();
    }

    private static boolean requiredBoolean(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isBoolean()) throw invalidRequest();
        return value.booleanValue();
    }

    private static int requiredInt(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isInt()) throw invalidRequest();
        return value.intValue();
    }

    private static AdminException invalidRequest() {
        return new AdminException(AdminErrorCode.INVALID_REQUEST);
    }
}
