package openflash_core.controller;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.ModelOption;
import openflash_core.service.impl.UserAiClientFactory;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl.AiProviderView;
import openflash_core.client.AiRuntimeCoreClient.ModelsSnapshot;
import openflash_core.common.AiErrorCode;
import openflash_core.dto.ApiResponse;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserAiConfigService;

@RestController
@RequestMapping("/api/settings")
public class UserAiConfigController {

    private final UserAiConfigService userAiConfigService;
    private final UnifiedAiSelectionServiceImpl selectionService;
    private final CurrentUserService currentUserService;
    private final UserAiClientFactory userAiClientFactory;
    private final AiModelDiscoveryServiceImpl aiModelDiscoveryService;

    public UserAiConfigController(
            UserAiConfigService userAiConfigService,
            UnifiedAiSelectionServiceImpl selectionService,
            CurrentUserService currentUserService,
            UserAiClientFactory userAiClientFactory,
            AiModelDiscoveryServiceImpl aiModelDiscoveryService) {
        this.userAiConfigService = userAiConfigService;
        this.selectionService = selectionService;
        this.currentUserService = currentUserService;
        this.userAiClientFactory = userAiClientFactory;
        this.aiModelDiscoveryService = aiModelDiscoveryService;
    }

    @GetMapping("/ai-config/providers")
    public ApiResponse<List<AiProviderView>> listProviders() {
        return ApiResponse.success(selectionService.listProviders(currentUserService.getCurrentUserId()));
    }

    @PostMapping("/ai-config/providers")
    public ApiResponse<Void> createProvider(@RequestBody AiProviderRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        userAiConfigService.createProvider(userId, request.displayName(), request.website(),
                request.note(), request.baseUrl(), request.apiKey(), request.model(),
                request.reasoningEffort());
        userAiClientFactory.evict(userId);
        return ApiResponse.success(null);
    }

    @PutMapping("/ai-config/providers/{providerKey}")
    public ApiResponse<Void> saveProvider(
            @PathVariable String providerKey, @RequestBody AiProviderRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        userAiConfigService.saveProvider(userId, providerKey, request.displayName(),
                request.website(), request.note(), request.baseUrl(), request.apiKey(), request.model(),
                request.reasoningEffort());
        userAiClientFactory.evict(userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/ai-config/providers/{providerKey}/activate")
    public ApiResponse<Void> activateProvider(
            @PathVariable String providerKey,
            @RequestParam(required = false) AiSource source) {
        if (source != AiSource.USER) {
            throw new openflash_core.common.AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        Long userId = currentUserService.getCurrentUserId();
        selectionService.activateUserProvider(userId, providerKey);
        userAiClientFactory.evict(userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/ai-config/platform-offerings/{offeringKey}/models")
    public ApiResponse<ModelsSnapshot> listPlatformModels(@PathVariable String offeringKey) {
        return ApiResponse.success(selectionService.listPlatformModels(
                currentUserService.getCurrentUserId(), offeringKey));
    }

    @PutMapping("/ai-config/platform-offerings/{offeringKey}/preference")
    public ApiResponse<Void> savePlatformPreference(
            @PathVariable String offeringKey, @RequestBody PlatformPreferenceRequest request) {
        selectionService.savePlatformCliPreference(currentUserService.getCurrentUserId(),
                offeringKey, request.model(), request.reasoningEffort());
        return ApiResponse.success(null);
    }

    @PostMapping("/ai-config/platform-offerings/{offeringKey}/activate")
    public ApiResponse<Void> activatePlatformOffering(@PathVariable String offeringKey) {
        Long userId = currentUserService.getCurrentUserId();
        selectionService.activatePlatformOffering(userId, offeringKey);
        userAiClientFactory.evict(userId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/ai-config/providers/{providerKey}")
    public ApiResponse<Void> deleteProvider(@PathVariable String providerKey) {
        Long userId = currentUserService.getCurrentUserId();
        userAiConfigService.deleteProvider(userId, providerKey);
        userAiClientFactory.evict(userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/ai-config/models/discover")
    public ApiResponse<List<ModelOption>> discoverModels(@RequestBody AiModelDiscoveryRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        String apiKey = userAiConfigService.resolveDiscoveryApiKey(
                userId, request.providerKey(), request.apiKey());
        return ApiResponse.success(aiModelDiscoveryService.discover(request.baseUrl(), apiKey));
    }

    public record AiModelDiscoveryRequest(String providerKey, String baseUrl, String apiKey) {}
    public record PlatformPreferenceRequest(String model, String reasoningEffort) {}
    public record AiProviderRequest(
            String displayName, String website, String note, String baseUrl,
            String apiKey, String model, String reasoningEffort) {}
}
