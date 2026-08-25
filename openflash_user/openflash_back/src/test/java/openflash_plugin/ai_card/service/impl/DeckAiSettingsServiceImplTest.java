package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand;
import openflash_core.mapper.DeckMapper;
import openflash_plugin.ai_card.mapper.DeckAiSettingsMapper;
import openflash_core.service.CurrentUserService;

class DeckAiSettingsServiceImplTest {

    /** 验证没有保存行时，页面拿到默认关闭和空提示词。 */
    @Test
    void getByDeckIdReturnsDefaultsWhenMapperHasNoRow() {
        Fixture f = new Fixture();
        when(f.deckAiSettingsMapper.findByDeckId(11L)).thenReturn(null);

        DeckAiSettings result = f.service.getByDeckId(11L);

        assertEquals(11L, result.getDeckId());
        assertEquals(false, result.getAiExplanationEnabledA());
        assertEquals(false, result.getAiExplanationEnabledB());
        assertNull(result.getAiExplanationPromptA());
        assertNull(result.getAiExplanationPromptB());
        assertEquals(false, result.getAiCompletionEnabled());
        assertNull(result.getAiCompletionPrompt());
    }

    /** 验证已有保存行时，页面拿到数据库里的全部字段。 */
    @Test
    void getByDeckIdReturnsMappedRow() {
        Fixture f = new Fixture();
        DeckAiSettings stored = settings(11L, false, "A", "B", false, "completion");
        when(f.deckAiSettingsMapper.findByDeckId(11L)).thenReturn(stored);

        DeckAiSettings result = f.service.getByDeckId(11L);

        assertEquals(11L, result.getDeckId());
        assertEquals(false, result.getAiExplanationEnabledA());
        assertEquals(false, result.getAiExplanationEnabledB());
        assertEquals("A", result.getAiExplanationPromptA());
        assertEquals("B", result.getAiExplanationPromptB());
        assertEquals(false, result.getAiCompletionEnabled());
        assertEquals("completion", result.getAiCompletionPrompt());
    }

    /** 验证数据库里出现脏 AI 设置时，页面仍能看到默认关闭状态。 */
    @Test
    void getByDeckIdNormalizesInvalidStoredRow() {
        Fixture f = new Fixture();
        DeckAiSettings stored = settings(11L, null, "  A prompt  ", "   ", null, "  completion  ");
        when(f.deckAiSettingsMapper.findByDeckId(11L)).thenReturn(stored);

        DeckAiSettings result = f.service.getByDeckId(11L);

        assertEquals(11L, result.getDeckId());
        assertEquals(false, result.getAiExplanationEnabledA());
        assertEquals(false, result.getAiExplanationEnabledB());
        assertEquals("A prompt", result.getAiExplanationPromptA());
        assertNull(result.getAiExplanationPromptB());
        assertEquals(false, result.getAiCompletionEnabled());
        assertEquals("completion", result.getAiCompletionPrompt());
    }

    /** 验证页面读取 AI 设置时，服务层检查卡包属于当前用户。 */
    @Test
    void getForCurrentUserRejectsDeckNotOwnedByCurrentUser() {
        Fixture f = new Fixture();
        when(f.currentUserService.getCurrentUserId()).thenReturn(7L);
        when(f.deckMapper.findByIdAndUserId(11L, 7L)).thenReturn(null);

        AppException ex = assertThrows(AppException.class, () -> f.service.getForCurrentUser(11L));

        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
    }

    /** 验证页面读取 AI 设置时，当前用户拥有卡包才返回设置。 */
    @Test
    void getForCurrentUserReturnsSettingsWhenDeckOwnedByCurrentUser() {
        Fixture f = new Fixture();
        when(f.currentUserService.getCurrentUserId()).thenReturn(7L);
        when(f.deckMapper.findByIdAndUserId(11L, 7L)).thenReturn(new Deck());
        when(f.deckAiSettingsMapper.findByDeckId(11L)).thenReturn(null);

        DeckAiSettings result = f.service.getForCurrentUser(11L);

        assertEquals(11L, result.getDeckId());
        assertEquals(false, result.getAiExplanationEnabledA());
        assertEquals(false, result.getAiExplanationEnabledB());
    }

    /** 验证保存时会把整理后的设置交给 mapper 写入。 */
    @Test
    void saveCallsMapperUpsert() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(true, "A", "B", true, "completion");

        f.service.save(11L, command);

        ArgumentCaptor<DeckAiSettings> saved = ArgumentCaptor.forClass(DeckAiSettings.class);
        verify(f.deckAiSettingsMapper).upsert(saved.capture());
        assertEquals(11L, saved.getValue().getDeckId());
        assertEquals(true, saved.getValue().getAiExplanationEnabledA());
        assertEquals(true, saved.getValue().getAiExplanationEnabledB());
        assertEquals("A", saved.getValue().getAiExplanationPromptA());
        assertEquals("B", saved.getValue().getAiExplanationPromptB());
        assertEquals(true, saved.getValue().getAiCompletionEnabled());
        assertEquals("completion", saved.getValue().getAiCompletionPrompt());
        assertNotNull(saved.getValue().getUpdatedAt());
    }

    /** 验证空卡包编号不会保存，页面会拿到设置无效错误。 */
    @Test
    void saveRejectsNullDeckId() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(true, "A", "B", true, "completion");

        AppException ex = assertThrows(AppException.class, () -> f.service.save(null, command));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
    }

    /** 验证空保存请求不会触发空指针，页面会拿到设置无效错误。 */
    @Test
    void saveRejectsNullCommand() {
        Fixture f = new Fixture();

        AppException ex = assertThrows(AppException.class, () -> f.service.save(11L, null));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
    }

    /** 验证 AI 设置按全量保存处理，缺少开关时不会把功能默认为开启。 */
    @Test
    void saveRejectsIncompleteRequiredFields() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(null, null, null, true, null);

        AppException ex = assertThrows(AppException.class, () -> f.service.save(11L, command));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.deckAiSettingsMapper, never()).upsert(any());
    }

    /** 验证 aiExplanationEnabledA 为 null 时保存请求被拦截。 */
    @Test
    void saveRejectsNullAiExplanationEnabledA() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = new DeckAiSettingsUpdateCommand(
            null, true, null, null, true, null);

        AppException ex = assertThrows(AppException.class, () -> f.service.save(11L, command));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.deckAiSettingsMapper, never()).upsert(any());
    }

    /** 验证 aiExplanationEnabledB 为 null 时保存请求被拦截。 */
    @Test
    void saveRejectsNullAiExplanationEnabledB() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = new DeckAiSettingsUpdateCommand(
            true, null, null, null, true, null);

        AppException ex = assertThrows(AppException.class, () -> f.service.save(11L, command));

        assertEquals(ErrorCode.DECK_SETTINGS_INVALID, ex.getErrorCode());
        verify(f.deckAiSettingsMapper, never()).upsert(any());
    }

    /** 验证保存时 aiExplanationEnabledA/B 都写入 mapper。 */
    @Test
    void saveWritesAiEnabledAAndBToMapper() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = new DeckAiSettingsUpdateCommand(
            true, false, "A", "B", true, "completion");

        f.service.save(11L, command);

        ArgumentCaptor<DeckAiSettings> saved = ArgumentCaptor.forClass(DeckAiSettings.class);
        verify(f.deckAiSettingsMapper).upsert(saved.capture());
        assertEquals(true, saved.getValue().getAiExplanationEnabledA());
        assertEquals(false, saved.getValue().getAiExplanationEnabledB());
    }

    /** 验证页面保存 AI 设置时，服务层检查卡包属于当前用户。 */
    @Test
    void saveForCurrentUserRejectsDeckNotOwnedByCurrentUser() {
        Fixture f = new Fixture();
        when(f.currentUserService.getCurrentUserId()).thenReturn(7L);
        when(f.deckMapper.findByIdAndUserId(11L, 7L)).thenReturn(null);
        DeckAiSettingsUpdateCommand command = command(true, "A", "B", true, "completion");

        AppException ex = assertThrows(AppException.class, () -> f.service.saveForCurrentUser(11L, command));

        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
        verify(f.deckAiSettingsMapper, never()).upsert(any());
    }

    /** 验证 A/B 两面提示词独立保存。 */
    @Test
    void saveKeepsPromptAAndPromptBSeparate() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(true, "front prompt", "back prompt", true, "completion");

        DeckAiSettings result = f.service.save(11L, command);

        assertEquals("front prompt", result.getAiExplanationPromptA());
        assertEquals("back prompt", result.getAiExplanationPromptB());
    }

    /** 验证空提示词保持为空，补全开关能被用户关闭。 */
    @Test
    void saveKeepsNullPromptsAndAllowsCompletionDisabled() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(true, null, null, false, null);

        DeckAiSettings result = f.service.save(11L, command);

        assertEquals(true, result.getAiExplanationEnabledA());
        assertEquals(true, result.getAiExplanationEnabledB());
        assertNull(result.getAiExplanationPromptA());
        assertNull(result.getAiExplanationPromptB());
        assertEquals(false, result.getAiCompletionEnabled());
        assertNull(result.getAiCompletionPrompt());
        assertNotNull(result.getUpdatedAt());
    }

    /** 验证提示词保存前去掉首尾空格，纯空白提示词保存为空。 */
    @Test
    void saveTrimsPromptsAndStoresBlankAsNull() {
        Fixture f = new Fixture();
        DeckAiSettingsUpdateCommand command = command(
            true,
            "  explain front  ",
            "    ",
            true,
            "  complete back  "
        );

        DeckAiSettings result = f.service.save(11L, command);

        assertEquals("explain front", result.getAiExplanationPromptA());
        assertNull(result.getAiExplanationPromptB());
        assertEquals("complete back", result.getAiCompletionPrompt());
    }

    /** 生成测试用的已保存 AI 设置行（A/B 面均使用同一个 enabled 值）。 */
    private static DeckAiSettings settings(Long deckId, Boolean explanationEnabled,
            String promptA, String promptB, Boolean completionEnabled, String completionPrompt) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setDeckId(deckId);
        settings.setAiExplanationEnabledA(explanationEnabled);
        settings.setAiExplanationEnabledB(explanationEnabled);
        settings.setAiExplanationPromptA(promptA);
        settings.setAiExplanationPromptB(promptB);
        settings.setAiCompletionEnabled(completionEnabled);
        settings.setAiCompletionPrompt(completionPrompt);
        return settings;
    }

    /** 生成测试用的页面保存请求（A/B 面均使用同一个 enabled 值）。 */
    private static DeckAiSettingsUpdateCommand command(Boolean explanationEnabled,
            String promptA, String promptB, Boolean completionEnabled, String completionPrompt) {
        return new DeckAiSettingsUpdateCommand(
            explanationEnabled,
            explanationEnabled,
            promptA,
            promptB,
            completionEnabled,
            completionPrompt
        );
    }

    /** 准备服务测试需要的 mapper 和 service。 */
    private static class Fixture {
        final DeckAiSettingsMapper deckAiSettingsMapper = mock(DeckAiSettingsMapper.class);
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final DeckAiSettingsServiceImpl service;

        Fixture() {
            service = new DeckAiSettingsServiceImpl(deckAiSettingsMapper, currentUserService, deckMapper);
        }
    }
}
