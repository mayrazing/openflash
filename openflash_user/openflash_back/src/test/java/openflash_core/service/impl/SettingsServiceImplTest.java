package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.entity.UserSettings;
import openflash_core.mapper.UserSettingsMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.TypeRegistryService;

class SettingsServiceImplTest {

    @Test
    void updateSettingsReturnsSavedValuesWithoutReloading() {
        Fixture fixture = fixture();
        UserSettings existing = settings("light", true, "en");
        when(fixture.userSettingsMapper.findByUserId(7L)).thenReturn(existing);

        UserSettings result = fixture.service.updateSettings("dark", false, null, null);

        assertEquals("dark", result.getTheme());
        assertEquals(false, result.getSoundEnabled());
        assertEquals("en", result.getLanguage());
        verify(fixture.userSettingsMapper, times(1)).findByUserId(7L);
        verify(fixture.userSettingsMapper, times(1)).update(existing);
    }

    @Test
    void updateSettingsWithValidLanguagePersistsIt() {
        Fixture fixture = fixture();
        UserSettings existing = settings("light", true, "zh");
        when(fixture.userSettingsMapper.findByUserId(7L)).thenReturn(existing);

        UserSettings result = fixture.service.updateSettings(null, null, null, "en");

        assertEquals("en", result.getLanguage());
        verify(fixture.userSettingsMapper, times(1)).update(existing);
    }

    @Test
    void updateSettingsWithInvalidLanguageThrows() {
        Fixture fixture = fixture();
        UserSettings existing = settings("light", true, "zh");
        when(fixture.userSettingsMapper.findByUserId(7L)).thenReturn(existing);

        AppException error = assertThrows(
            AppException.class,
            () -> fixture.service.updateSettings(null, null, null, "xx")
        );

        assertEquals(40033, error.getErrorCode().value());
        verify(fixture.userSettingsMapper, never()).update(existing);
    }

    @Test
    void updateSettingsWithNullLanguageDoesNotOverrideExisting() {
        Fixture fixture = fixture();
        UserSettings existing = settings("light", true, "zh");
        when(fixture.userSettingsMapper.findByUserId(7L)).thenReturn(existing);

        UserSettings result = fixture.service.updateSettings(null, null, null, null);

        assertEquals("zh", result.getLanguage());
        verify(fixture.userSettingsMapper, times(1)).update(existing);
    }

    @Test
    void updateSettingsWithUnchangedDisabledLanguageSavesOtherFields() {
        Fixture fixture = fixture();
        UserSettings existing = settings("light", true, "fi");
        when(fixture.userSettingsMapper.findByUserId(7L)).thenReturn(existing);
        when(fixture.typeRegistryService.getEnabledLanguageKeys()).thenReturn(List.of("en"));

        UserSettings result = fixture.service.updateSettings("dark", null, null, "fi");

        assertEquals("dark", result.getTheme());
        assertEquals("fi", result.getLanguage());
        verify(fixture.typeRegistryService, never()).getEnabledLanguageKeys();
        verify(fixture.userSettingsMapper, times(1)).update(existing);
    }

    private Fixture fixture() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserSettingsMapper userSettingsMapper = mock(UserSettingsMapper.class);
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(currentUserService.getCurrentUserId()).thenReturn(7L);
        when(typeRegistryService.getEnabledLanguageKeys()).thenReturn(List.of("zh", "en", "fi", "de"));
        return new Fixture(
            new SettingsServiceImpl(currentUserService, userSettingsMapper, typeRegistryService),
            userSettingsMapper,
            typeRegistryService
        );
    }

    private UserSettings settings(String theme, Boolean soundEnabled, String language) {
        UserSettings settings = new UserSettings();
        settings.setId(3L);
        settings.setUserId(7L);
        settings.setTheme(theme);
        settings.setSoundEnabled(soundEnabled);
        settings.setLanguage(language);
        return settings;
    }

    private record Fixture(
        SettingsServiceImpl service,
        UserSettingsMapper userSettingsMapper,
        TypeRegistryService typeRegistryService
    ) {
    }
}
