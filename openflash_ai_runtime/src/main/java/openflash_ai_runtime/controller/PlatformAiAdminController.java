package openflash_ai_runtime.controller;

import java.util.List;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformAiCatalogService.ConnectionView;
import openflash_ai_runtime.service.PlatformAiCatalogService.OfferingView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理作用域的平台 AI catalog API, 只返回非敏感 DTO. */
@RestController
@RequestMapping("/api/internal/admin/platform-ai")
public class PlatformAiAdminController {

    private final PlatformAiCatalogService service;

    public PlatformAiAdminController(PlatformAiCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PlatformAiPageResponse page() {
        var page = service.page();
        return new PlatformAiPageResponse(
                page.runtimeStatus(), page.connections().stream().map(this::connection).toList());
    }

    @PostMapping("/connections")
    public ConnectionResponse createConnection(@RequestBody CreateConnectionRequest request) {
        return connection(service.createConnection(
                new PlatformAiCatalogService.CreateConnectionCommand(
                        request.kind(), request.protocol(), request.cliKey(),
                        request.displayName(), request.baseUrl(), request.sortOrder())));
    }

    @PutMapping("/connections/{connectionKey}")
    public ConnectionResponse updateConnection(
            @PathVariable String connectionKey,
            @RequestBody UpdateConnectionRequest request) {
        return connection(service.updateConnection(
                connectionKey,
                new PlatformAiCatalogService.UpdateConnectionCommand(
                        request.baseUrl(), request.enabled(), request.sortOrder())));
    }

    @PutMapping("/connections/{connectionKey}/credentials")
    public void replaceCredentials(
            @PathVariable String connectionKey,
            @RequestBody ReplaceCredentialsRequest request) {
        service.replaceCredentials(connectionKey, request.apiKey());
    }

    @DeleteMapping("/connections/{connectionKey}")
    public void deleteConnection(@PathVariable String connectionKey) {
        service.deleteConnection(connectionKey);
    }

    @PostMapping("/connections/{connectionKey}/models/discover")
    public List<DiscoveredModel> discoverModels(@PathVariable String connectionKey) {
        return service.discoverModels(connectionKey).stream().map(DiscoveredModel::new).toList();
    }

    @PostMapping("/models/discover")
    public List<DiscoveredModel> discoverModels(@RequestBody DiscoverModelsRequest request) {
        return service.discoverModels(request.baseUrl(), request.apiKey()).stream()
                .map(DiscoveredModel::new).toList();
    }

    @PostMapping("/connections/{connectionKey}/offerings")
    public OfferingResponse createOffering(
            @PathVariable String connectionKey,
            @RequestBody CreateOfferingRequest request) {
        return offering(service.createOffering(
                connectionKey,
                new PlatformAiCatalogService.CreateOfferingCommand(
                        request.modelKey(), request.sortOrder())));
    }

    @PutMapping("/offerings/{offeringKey}")
    public OfferingResponse updateOffering(
            @PathVariable String offeringKey,
            @RequestBody UpdateOfferingRequest request) {
        return offering(service.updateOffering(
                offeringKey,
                new PlatformAiCatalogService.UpdateOfferingCommand(
                        request.modelKey(), request.enabled(), request.sortOrder())));
    }

    @DeleteMapping("/offerings/{offeringKey}")
    public void deleteOffering(@PathVariable String offeringKey) {
        service.deleteOffering(offeringKey);
    }

    @PutMapping("/offerings/{offeringKey}/access/default")
    public void setDefaultAccess(
            @PathVariable String offeringKey,
            @RequestBody SetDefaultAccessRequest request) {
        service.setDefaultAccess(offeringKey, request.enabled());
    }

    @PutMapping("/offerings/{offeringKey}/access/users/{userId}")
    public void setUserAccess(
            @PathVariable String offeringKey,
            @PathVariable long userId,
            @RequestBody SetUserAccessRequest request) {
        service.setUserAccess(offeringKey, userId, request.enabled());
    }

    @DeleteMapping("/offerings/{offeringKey}/access/users/{userId}")
    public void deleteUserAccess(
            @PathVariable String offeringKey,
            @PathVariable long userId) {
        service.deleteUserAccess(offeringKey, userId);
    }

    private ConnectionResponse connection(ConnectionView source) {
        return new ConnectionResponse(
                source.connectionKey(), "PLATFORM", source.kind(), source.protocol(),
                source.displayName(), source.baseUrl(),
                source.credentialsConfigured(), source.enabled(), source.sortOrder(),
                source.offerings().stream().map(this::offering).toList());
    }

    private OfferingResponse offering(OfferingView source) {
        return new OfferingResponse(
                source.offeringKey(), "PLATFORM", source.modelKey(), source.enabled(),
                source.defaultAccess(), source.sortOrder(), source.runtimeStatus());
    }

    public record PlatformAiPageResponse(
            String runtimeStatus,
            List<ConnectionResponse> connections) {
    }

    public record ConnectionResponse(
            String connectionKey,
            String source,
            String kind,
            String protocol,
            String displayName,
            String baseUrl,
            boolean credentialsConfigured,
            boolean enabled,
            int sortOrder,
            List<OfferingResponse> offerings) {
        public ConnectionResponse(
                String connectionKey, String source, String kind, String protocol,
                String baseUrl, boolean credentialsConfigured, boolean enabled,
                int sortOrder, List<OfferingResponse> offerings) {
            this(connectionKey, source, kind, protocol, null, baseUrl,
                    credentialsConfigured, enabled, sortOrder, offerings);
        }
    }

    public record OfferingResponse(
            String offeringKey,
            String source,
            String modelKey,
            boolean enabled,
            boolean defaultAccess,
            int sortOrder,
            String runtimeStatus) {
    }

    public record CreateConnectionRequest(
            String kind, String protocol, String cliKey, String displayName,
            String baseUrl, int sortOrder) {
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
