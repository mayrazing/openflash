package openflash_core.controller;

import java.util.List;
import openflash_core.dto.ApiResponse;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.PluginCatalogItem;
import openflash_core.service.CurrentUserService;
import openflash_core.service.FeatureFlagService;
import openflash_core.service.PluginCatalogService;
import openflash_core.service.PluginInstallService;
import openflash_core.service.impl.PluginRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供前端查询插件、目录和安装管理的核心接口。 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private static final String FEATURE_MARKETPLACE = "feature.plugin-marketplace";

    private final PluginRegistry registry;
    private final CurrentUserService currentUserService;
    private final PluginInstallService installService;
    private final PluginCatalogService catalogService;
    private final FeatureFlagService featureFlagService;

    public PluginController(PluginRegistry registry,
                            CurrentUserService currentUserService,
                            PluginInstallService installService,
                            PluginCatalogService catalogService,
                            FeatureFlagService featureFlagService) {
        this.registry = registry;
        this.currentUserService = currentUserService;
        this.installService = installService;
        this.catalogService = catalogService;
        this.featureFlagService = featureFlagService;
    }

    /** 插件市场关闭时抛出统一错误码 50301。 */
    private void ensureMarketplaceEnabled() {
        if (!featureFlagService.isEnabled(FEATURE_MARKETPLACE)) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }

    /** 返回当前所有启用插件的 ID 列表。 */
    @GetMapping("/active")
    public ApiResponse<List<String>> getActivePlugins() {
        return ApiResponse.success(registry.activePluginIds());
    }

    /** 返回插件目录（全局启用的所有插件条目）。 */
    @GetMapping("/catalog")
    public ApiResponse<List<PluginCatalogItem>> getCatalog() {
        ensureMarketplaceEnabled();
        return ApiResponse.success(catalogService.catalog());
    }

    /** 返回指定卡包中当前用户已安装的插件 ID 列表。 */
    @GetMapping("/installed")
    public ApiResponse<List<String>> getInstalledPlugins(@RequestParam Long deckId) {
        ensureMarketplaceEnabled();
        Long userId = currentUserService.getCurrentUserId();
        return ApiResponse.success(installService.installedPluginIds(userId, deckId));
    }

    /** 批量安装/卸载插件：对 installDeckIds 安装，对 uninstallDeckIds 卸载。 */
    @PostMapping("/install")
    public ApiResponse<Void> install(@RequestBody InstallRequest request) {
        ensureMarketplaceEnabled();
        Long userId = currentUserService.getCurrentUserId();
        if (request.installDeckIds() != null) {
            for (Long deckId : request.installDeckIds()) {
                installService.install(userId, deckId, request.pluginId());
            }
        }
        if (request.uninstallDeckIds() != null) {
            for (Long deckId : request.uninstallDeckIds()) {
                installService.uninstall(userId, deckId, request.pluginId());
            }
        }
        return ApiResponse.success(null);
    }

    /** 安装请求体。 */
    public record InstallRequest(String pluginId,
                                  List<Long> installDeckIds,
                                  List<Long> uninstallDeckIds) {}
}
