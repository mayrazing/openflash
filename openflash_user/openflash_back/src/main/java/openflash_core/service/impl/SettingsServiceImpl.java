package openflash_core.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.UserSettings;
import openflash_core.mapper.UserSettingsMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.SettingsService;
import openflash_core.service.TypeRegistryService;

@Service
public class SettingsServiceImpl implements SettingsService {

    private final CurrentUserService currentUserService;
    private final UserSettingsMapper userSettingsMapper;
    private final TypeRegistryService typeRegistryService;

    public SettingsServiceImpl(
        CurrentUserService currentUserService,
        UserSettingsMapper userSettingsMapper,
        TypeRegistryService typeRegistryService
    ) {
        this.currentUserService = currentUserService;
        this.userSettingsMapper = userSettingsMapper;
        this.typeRegistryService = typeRegistryService;
    }

    @Override
    public UserSettings getSettings() {
        Long userId = currentUserService.getCurrentUserId();
        UserSettings settings = userSettingsMapper.findByUserId(userId);
        if (settings == null) {
            throw new AppException(ErrorCode.USER_SETTINGS_NOT_FOUND);
        }
        return settings;
    }

    @Override
    @Transactional
    public UserSettings updateSettings(
        String theme,
        Boolean soundEnabled,
        LocalDateTime lastExportedAt,
        String language
    ) {
        Long userId = currentUserService.getCurrentUserId();
        UserSettings current = userSettingsMapper.findByUserId(userId);
        if (current == null) {
            throw new AppException(ErrorCode.USER_SETTINGS_NOT_FOUND);
        }
        if (theme != null && !theme.trim().isEmpty()) {
            current.setTheme(theme.trim());
        }
        if (soundEnabled != null) {
            current.setSoundEnabled(soundEnabled);
        }
        if (lastExportedAt != null) {
            current.setLastExportedAt(lastExportedAt);
        }
        if (language != null) {
            String nextLanguage = language.trim();
            boolean languageChanged = !nextLanguage.equals(current.getLanguage());
            if (languageChanged && !typeRegistryService.getEnabledLanguageKeys().contains(nextLanguage)) {
                throw new AppException(ErrorCode.UNSUPPORTED_LANGUAGE);
            }
            current.setLanguage(nextLanguage);
        }
        userSettingsMapper.update(current);
        return current;
    }
}
