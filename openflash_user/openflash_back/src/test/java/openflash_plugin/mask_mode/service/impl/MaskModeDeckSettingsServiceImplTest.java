package openflash_plugin.mask_mode.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.service.CurrentUserService;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;
import openflash_plugin.mask_mode.dto.MaskModeDeckSettingsUpdateCommand;
import openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper;

class MaskModeDeckSettingsServiceImplTest {

    @Test
    void getForCurrentUserReturnsDefaultsWhenNoRow() {
        Fixture f = new Fixture();
        when(f.maskModeDeckSettingsMapper.findByDeckId(1L)).thenReturn(null);

        MaskModeDeckSettings result = f.service.getForCurrentUser(1L);

        assertEquals(1L, result.deckId());
        assertEquals("random", result.mode());
        assertEquals(true, result.enabled());
    }

    @Test
    void getForCurrentUserFallsBackToDefaultWhenStoredModeIsInvalid() {
        // DB 因历史 migration 或手工改产生脏值时，设置页两个 radio 都不选中。
        // 读路径必须用白名单过滤，落到 DEFAULT_MODE 保证 UI 始终有合法选项。
        // 脏 mode 回退时 enabled 保留 DB 值，避免顺手把用户关闭的开关复位。
        Fixture f = new Fixture();
        when(f.maskModeDeckSettingsMapper.findByDeckId(1L))
            .thenReturn(new MaskModeDeckSettings(1L, "partial", false));

        MaskModeDeckSettings result = f.service.getForCurrentUser(1L);

        assertEquals(1L, result.deckId());
        assertEquals("random", result.mode());
        assertEquals(false, result.enabled());
    }

    @Test
    void getForCurrentUserFallsBackToDefaultWhenStoredModeIsNull() {
        Fixture f = new Fixture();
        when(f.maskModeDeckSettingsMapper.findByDeckId(1L))
            .thenReturn(new MaskModeDeckSettings(1L, null, true));

        MaskModeDeckSettings result = f.service.getForCurrentUser(1L);

        assertEquals("random", result.mode());
        assertEquals(true, result.enabled());
    }

    @Test
    void getForCurrentUserReturnsStoredEnabledFalse() {
        Fixture f = new Fixture();
        when(f.maskModeDeckSettingsMapper.findByDeckId(1L))
            .thenReturn(new MaskModeDeckSettings(1L, "full", false));

        MaskModeDeckSettings result = f.service.getForCurrentUser(1L);

        assertEquals("full", result.mode());
        assertEquals(false, result.enabled());
    }

    @Test
    void saveForCurrentUserUpsertsPluginSettings() {
        Fixture f = new Fixture();

        MaskModeDeckSettings result =
            f.service.saveForCurrentUser(1L, new MaskModeDeckSettingsUpdateCommand("random", true));

        assertEquals("random", result.mode());
        assertEquals(true, result.enabled());
        ArgumentCaptor<MaskModeDeckSettings> captor = ArgumentCaptor.forClass(MaskModeDeckSettings.class);
        verify(f.maskModeDeckSettingsMapper).upsert(captor.capture());
        assertEquals(new MaskModeDeckSettings(1L, "random", true), captor.getValue());
        verify(f.maskModeDeckSettingsMapper, never()).findByDeckId(1L);
    }

    @Test
    void saveForCurrentUserPersistsEnabledFalse() {
        Fixture f = new Fixture();

        MaskModeDeckSettings result =
            f.service.saveForCurrentUser(1L, new MaskModeDeckSettingsUpdateCommand("full", false));

        assertEquals("full", result.mode());
        assertEquals(false, result.enabled());
        ArgumentCaptor<MaskModeDeckSettings> captor = ArgumentCaptor.forClass(MaskModeDeckSettings.class);
        verify(f.maskModeDeckSettingsMapper).upsert(captor.capture());
        assertEquals(new MaskModeDeckSettings(1L, "full", false), captor.getValue());
    }

    @Test
    void saveForCurrentUserRejectsNullMode() {
        Fixture f = new Fixture();

        AppException ex = assertThrows(AppException.class,
            () -> f.service.saveForCurrentUser(1L, new MaskModeDeckSettingsUpdateCommand(null, true)));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.maskModeDeckSettingsMapper, never()).upsert(any());
    }

    @Test
    void saveForCurrentUserRejectsIllegalMode() {
        Fixture f = new Fixture();

        AppException ex = assertThrows(AppException.class,
            () -> f.service.saveForCurrentUser(1L, new MaskModeDeckSettingsUpdateCommand("partial", true)));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.maskModeDeckSettingsMapper, never()).upsert(any());
    }

    @Test
    void saveForCurrentUserRejectsNullEnabled() {
        Fixture f = new Fixture();

        AppException ex = assertThrows(AppException.class,
            () -> f.service.saveForCurrentUser(1L, new MaskModeDeckSettingsUpdateCommand("random", null)));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.maskModeDeckSettingsMapper, never()).upsert(any());
    }

    @Test
    void getForCurrentUserThrowsDeckNotFoundWhenDeckNotOwned() {
        Fixture f = new Fixture();
        when(f.deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(null);

        AppException ex = assertThrows(AppException.class, () -> f.service.getForCurrentUser(1L));

        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
        verify(f.maskModeDeckSettingsMapper, never()).findByDeckId(1L);
    }

    private static class Fixture {
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final MaskModeDeckSettingsMapper maskModeDeckSettingsMapper = mock(MaskModeDeckSettingsMapper.class);
        final MaskModeDeckSettingsServiceImpl service;

        /** 初始化当前用户拥有 1 号卡包的服务测试环境。 */
        Fixture() {
            service = new MaskModeDeckSettingsServiceImpl(currentUserService, deckMapper, maskModeDeckSettingsMapper);
            when(currentUserService.getCurrentUserId()).thenReturn(7L);
            Deck deck = new Deck();
            deck.setId(1L);
            deck.setUserId(7L);
            when(deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(deck);
        }
    }
}
