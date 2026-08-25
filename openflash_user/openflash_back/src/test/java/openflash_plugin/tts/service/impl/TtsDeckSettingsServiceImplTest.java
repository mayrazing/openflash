package openflash_plugin.tts.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.TypeRegistryMapper;
import openflash_core.service.CurrentUserService;
import openflash_plugin.tts.dto.TtsDeckSettingsUpdateCommand;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.mapper.TtsDeckSettingsMapper;

class TtsDeckSettingsServiceImplTest {

    @Test
    void getForCurrentUserReturnsCosyvoice3DefaultWhenNoRow() {
        Fixture f = new Fixture();
        when(f.ttsDeckSettingsMapper.findByDeckId(1L)).thenReturn(null);

        TtsDeckSettings result = f.service.getForCurrentUser(1L);

        assertEquals(new TtsDeckSettings(1L, false, false, "cosyvoice3"), result);
    }

    @Test
    void saveForCurrentUserUpdatesAutoSpeakAndDefaultModelTogether() {
        Fixture f = new Fixture();
        when(f.ttsDeckSettingsMapper.findByDeckId(1L))
            .thenReturn(new TtsDeckSettings(1L, false, false, "cosyvoice3"));

        TtsDeckSettings result = f.service.saveForCurrentUser(
            1L, new TtsDeckSettingsUpdateCommand(true, false, "piper"));

        assertEquals(new TtsDeckSettings(1L, true, false, "piper"), result);
        ArgumentCaptor<TtsDeckSettings> captor = ArgumentCaptor.forClass(TtsDeckSettings.class);
        verify(f.ttsDeckSettingsMapper).update(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void saveForCurrentUserRejectsModelDisabledInRegistry() {
        Fixture f = new Fixture();
        when(f.typeRegistryMapper.findEnabledItemKeys("tts_engine"))
            .thenReturn(List.of("cosyvoice3"));

        AppException error = assertThrows(AppException.class, () -> f.service.saveForCurrentUser(
            1L, new TtsDeckSettingsUpdateCommand(false, false, "piper")));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, error.getErrorCode());
        verify(f.ttsDeckSettingsMapper, never()).insert(any());
        verify(f.ttsDeckSettingsMapper, never()).update(any());
    }

    @Test
    void getEnabledEnginesFiltersUnknownAndDuplicateRegistryRows() {
        Fixture f = new Fixture();
        when(f.typeRegistryMapper.findEnabledItemKeys("tts_engine"))
            .thenReturn(List.of("piper", "unknown", "cosyvoice3", "piper"));

        assertEquals(List.of("piper", "cosyvoice3"), f.service.getEnabledEngines());
    }

    @Test
    void savedDisabledModelFallsBackToFirstEnabledModel() {
        Fixture f = new Fixture();
        when(f.typeRegistryMapper.findEnabledItemKeys("tts_engine")).thenReturn(List.of("piper"));
        when(f.ttsDeckSettingsMapper.findByDeckId(1L))
            .thenReturn(new TtsDeckSettings(1L, true, false, "cosyvoice3"));

        assertEquals(
            new TtsDeckSettings(1L, true, false, "piper"),
            f.service.getForCurrentUser(1L));
    }

    @Test
    void getForCurrentUserRejectsDeckNotOwnedByCurrentUser() {
        Fixture f = new Fixture();
        when(f.deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(null);

        AppException error = assertThrows(
            AppException.class, () -> f.service.getForCurrentUser(1L));

        assertEquals(ErrorCode.DECK_NOT_FOUND, error.getErrorCode());
        verify(f.ttsDeckSettingsMapper, never()).findByDeckId(1L);
    }

    private static class Fixture {
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final TtsDeckSettingsMapper ttsDeckSettingsMapper = mock(TtsDeckSettingsMapper.class);
        final TypeRegistryMapper typeRegistryMapper = mock(TypeRegistryMapper.class);
        final TtsDeckSettingsServiceImpl service;

        Fixture() {
            service = new TtsDeckSettingsServiceImpl(
                currentUserService, deckMapper, ttsDeckSettingsMapper, typeRegistryMapper);
            when(currentUserService.getCurrentUserId()).thenReturn(7L);
            when(typeRegistryMapper.findEnabledItemKeys("tts_engine"))
                .thenReturn(List.of("cosyvoice3", "piper"));
            Deck deck = new Deck();
            deck.setId(1L);
            deck.setUserId(7L);
            when(deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(deck);
        }
    }
}
