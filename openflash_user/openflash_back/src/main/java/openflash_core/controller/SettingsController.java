package openflash_core.controller;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.PracticeModeOption;
import openflash_core.entity.UserSettings;
import openflash_core.service.SettingsService;
import openflash_core.service.TypeRegistryService;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final TypeRegistryService typeRegistryService;

    public SettingsController(SettingsService settingsService, TypeRegistryService typeRegistryService) {
        this.settingsService = settingsService;
        this.typeRegistryService = typeRegistryService;
    }

    /**
     * 读取当前用户的全局设置，页面只展示全局生效的项目。
     */
    @GetMapping
    public ApiResponse<UserSettings> getSettings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    /**
     * 读取设置页可切换的界面语言按钮。
     */
    @GetMapping("/languages")
    public ApiResponse<List<PracticeModeOption>> getLanguages() {
        return ApiResponse.success(typeRegistryService.getEnabledLanguageOptions());
    }

    @PutMapping
    public ApiResponse<UserSettings> updateSettings(@RequestBody SettingsRequest request) {
        return ApiResponse.success(
            settingsService.updateSettings(
                request.theme(),
                request.soundEnabled(),
                request.lastExportedAt(),
                request.language()
            )
        );
    }

    public record SettingsRequest(
        String theme,
        Boolean soundEnabled,
        LocalDateTime lastExportedAt,
        String language
    ) {}
}
