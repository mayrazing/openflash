package openflash_core.service;

import java.time.LocalDateTime;
import openflash_core.entity.UserSettings;

public interface SettingsService {

    UserSettings getSettings();

    UserSettings updateSettings(
        String theme,
        Boolean soundEnabled,
        LocalDateTime lastExportedAt,
        String language
    );
}
