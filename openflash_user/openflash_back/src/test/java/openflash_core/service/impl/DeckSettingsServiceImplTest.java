package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckSettings;
import openflash_core.dto.DeckSettingsUpdateCommand;
import openflash_core.entity.PracticeReviewLoadProfile;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.service.CurrentUserService;

class DeckSettingsServiceImplTest {

    @Test
    void createDefaultSettingsReturnsDefaultsWithoutTimestamp() {
        DeckSettings settings = new Fixture().service.createDefaultSettings(42L);

        assertEquals(42L, settings.getDeckId());
        assertEquals(10, settings.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.9000"), settings.getTargetRetention());
        assertEquals(PracticeReviewLoadProfile.STANDARD.key(), settings.getReviewLoadProfile());
        assertEquals(true, settings.getDuplicateSideAEnabled());
        assertEquals(false, settings.getDuplicateSideBEnabled());
        assertNull(settings.getUpdatedAt());
    }

    @Test
    void createDefaultSettingsForInsertSetsUpdatedAt() {
        DeckSettings settings = new Fixture().service.createDefaultSettingsForInsert(42L);

        assertEquals(42L, settings.getDeckId());
        assertNotNull(settings.getUpdatedAt());
    }

    @Test
    void defaultReviewLoadProfileUsesEnumKeyInsteadOfLiteralString() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/openflash_core/service/impl/DeckSettingsServiceImpl.java"));

        assertFalse(source.contains("DEFAULT_REVIEW_LOAD_PROFILE = \"standard\""));
    }

    // ── getSettings ──────────────────────────────────────────────

    @Test
    void getSettingsReturnsMappedRow() {
        Fixture f = new Fixture();
        DeckSettings stored = deckSettings(5, new BigDecimal("0.8500"), "relaxed", false, true);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(stored);

        DeckSettings result = f.service.getSettings(1L);

        assertEquals(5, result.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.8500"), result.getTargetRetention());
        assertEquals("relaxed", result.getReviewLoadProfile());
    }

    @Test
    void getSettingsThrows404WhenDeckNotOwnedByUser() {
        Fixture f = new Fixture();
        when(f.deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(null);

        assertThrows(AppException.class, () -> f.service.getSettings(1L));
    }

    @Test
    void getSettingsReturnsDefaultsWhenNoRow() {
        Fixture f = new Fixture();
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(null);

        DeckSettings result = f.service.getSettings(1L);

        assertEquals(10, result.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.9000"), result.getTargetRetention());
        assertEquals("standard", result.getReviewLoadProfile());
    }

    @Test
    void getSettingsClampsStoredValuesToSupportedRange() {
        Fixture f = new Fixture();
        DeckSettings stored = deckSettings(999, new BigDecimal("1.5000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(stored);

        DeckSettings result = f.service.getSettings(1L);

        assertEquals(50, result.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.9700"), result.getTargetRetention());
    }

    // ── updateSettings ───────────────────────────────────────────

    @Test
    void updateSettingsWritesNewValues() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        f.service.updateSettings(1L, command(20, new BigDecimal("0.8500"), "relaxed", true, true));

        verify(f.deckSettingsMapper).update(any(DeckSettings.class));
    }

    @Test
    void updateSettingsClampsTooHighNewCardsPerDay() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        DeckSettings result = f.service.updateSettings(
            1L, command(999, new BigDecimal("0.9000"), "standard", true, false));

        assertEquals(50, result.getNewCardsPerDay());
        verify(f.deckSettingsMapper).update(any(DeckSettings.class));
    }

    @Test
    void updateSettingsClampsTargetRetentionToSupportedRange() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        DeckSettings tooLow = f.service.updateSettings(
            1L, command(10, new BigDecimal("0.1000"), "standard", true, false));
        assertEquals(new BigDecimal("0.7000"), tooLow.getTargetRetention());

        DeckSettings tooHigh = f.service.updateSettings(
            1L, command(10, new BigDecimal("1.5000"), "standard", true, false));
        assertEquals(new BigDecimal("0.9700"), tooHigh.getTargetRetention());
    }

    @Test
    void updateSettingsInsertsRowWhenNoRowExists() {
        Fixture f = new Fixture();
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(null);

        f.service.updateSettings(1L, command(20, new BigDecimal("0.8500"), "relaxed",
            true, true));

        verify(f.deckSettingsMapper).insert(any(DeckSettings.class));
        verify(f.deckSettingsMapper, never()).update(any());
    }

    @Test
    void updateSettingsRejectsIncompleteFullPayload() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        assertThrows(AppException.class,
            () -> f.service.updateSettings(1L, command(null, new BigDecimal("0.9000"), "standard", true, false)));
        verify(f.deckSettingsMapper, never()).update(any());
    }

    @Test
    void updateSettingsRejectsUnsupportedReviewLoadProfile() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        assertThrows(AppException.class,
            () -> f.service.updateSettings(1L, command(10, new BigDecimal("0.9000"), "garbage", true, false)));
        verify(f.deckSettingsMapper, never()).update(any());
    }

    @Test
    void updateSettingsThrows404WhenDeckNotOwned() {
        Fixture f = new Fixture();
        when(f.deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(null);

        assertThrows(AppException.class,
            () -> f.service.updateSettings(1L, command(10, new BigDecimal("0.9000"), "standard", true, false)));
        verify(f.deckSettingsMapper, never()).update(any());
    }

    @Test
    void updateSettingsReturnsSavedValuesWithoutReloading() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        DeckSettings result = f.service.updateSettings(
            1L, command(20, new BigDecimal("0.8500"), "relaxed", false, true));

        assertEquals(20, result.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.8500"), result.getTargetRetention());
        assertEquals("relaxed", result.getReviewLoadProfile());
        assertEquals(false, result.getDuplicateSideAEnabled());
        assertEquals(true, result.getDuplicateSideBEnabled());
        verify(f.deckMapper, times(1)).findByIdAndUserId(1L, 7L);
        verify(f.deckSettingsMapper, times(1)).findByDeckId(1L);
    }

    @Test
    void updateSettingsSkipsWriteWhenValuesDoNotChange() {
        Fixture f = new Fixture();
        DeckSettings existing = deckSettings(10, new BigDecimal("0.9000"), "standard", true, false);
        when(f.deckSettingsMapper.findByDeckId(1L)).thenReturn(existing);

        DeckSettings result = f.service.updateSettings(
            1L, command(10, new BigDecimal("0.9000"), "standard", true, false));

        assertEquals(10, result.getNewCardsPerDay());
        assertEquals(new BigDecimal("0.9000"), result.getTargetRetention());
        assertEquals("standard", result.getReviewLoadProfile());
        verify(f.deckSettingsMapper, never()).update(any());
        verify(f.deckSettingsMapper, never()).insert(any());
    }

    // ── helpers ──────────────────────────────────────────────────

    private static DeckSettings deckSettings(int newCards, BigDecimal retention, String profile,
            boolean dedupA, boolean dedupB) {
        DeckSettings s = new DeckSettings();
        s.setNewCardsPerDay(newCards);
        s.setTargetRetention(retention);
        s.setReviewLoadProfile(profile);
        s.setDuplicateSideAEnabled(dedupA);
        s.setDuplicateSideBEnabled(dedupB);
        return s;
    }

    private static DeckSettingsUpdateCommand command(Integer newCards, BigDecimal retention, String profile,
            boolean dedupA, boolean dedupB) {
        return new DeckSettingsUpdateCommand(newCards, retention, profile, dedupA, dedupB);
    }

    private static class Fixture {
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        final DeckSettingsServiceImpl service;

        Fixture() {
            service = new DeckSettingsServiceImpl(currentUserService, deckMapper, deckSettingsMapper);
            when(currentUserService.getCurrentUserId()).thenReturn(7L);
            Deck deck = new Deck();
            deck.setId(1L);
            deck.setUserId(7L);
            when(deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(deck);
        }
    }
}
