package openflash_plugin.ai_card.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.service.CurrentUserService;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand;
import openflash_plugin.ai_card.mapper.DeckAiSettingsMapper;

@Service
public class DeckAiSettingsServiceImpl implements DeckAiSettingsService {

    private final DeckAiSettingsMapper deckAiSettingsMapper;
    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;

    /** 注入卡包 AI 设置读写入口。 */
    public DeckAiSettingsServiceImpl(
        DeckAiSettingsMapper deckAiSettingsMapper,
        CurrentUserService currentUserService,
        DeckMapper deckMapper
    ) {
        this.deckAiSettingsMapper = deckAiSettingsMapper;
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
    }

    /** 返回卡包 AI 设置；用户还没保存过时给页面默认关闭状态。 */
    @Override
    public DeckAiSettings getByDeckId(Long deckId) {
        DeckAiSettings settings = deckAiSettingsMapper.findByDeckId(deckId);
        if (settings == null) {
            return createDefaultSettings(deckId);
        }
        return normalizeSettings(settings);
    }

    /** 返回当前用户拥有的卡包 AI 设置；不属于当前用户时返回卡包不存在错误。 */
    @Override
    public DeckAiSettings getForCurrentUser(Long deckId) {
        requireDeckOwnership(deckId);
        return getByDeckId(deckId);
    }

    /** 保存卡包 AI 设置。 */
    @Override
    public DeckAiSettings save(Long deckId, DeckAiSettingsUpdateCommand command) {
        validateSaveRequest(deckId, command);
        DeckAiSettings entity = new DeckAiSettings();
        entity.setDeckId(deckId);
        entity.setAiExplanationEnabledA(command.aiExplanationEnabledA());
        entity.setAiExplanationEnabledB(command.aiExplanationEnabledB());
        entity.setAiExplanationPromptA(normalizePrompt(command.aiExplanationPromptA()));
        entity.setAiExplanationPromptB(normalizePrompt(command.aiExplanationPromptB()));
        entity.setAiCompletionEnabled(command.aiCompletionEnabled());
        entity.setAiCompletionPrompt(normalizePrompt(command.aiCompletionPrompt()));
        entity.setUpdatedAt(LocalDateTime.now());
        deckAiSettingsMapper.upsert(entity);
        return entity;
    }

    /** 保存当前用户拥有的卡包 AI 设置；不属于当前用户时返回卡包不存在错误。 */
    @Override
    public DeckAiSettings saveForCurrentUser(Long deckId, DeckAiSettingsUpdateCommand command) {
        requireDeckOwnership(deckId);
        return save(deckId, command);
    }

    private DeckAiSettings createDefaultSettings(Long deckId) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setDeckId(deckId);
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(false);
        settings.setAiExplanationPromptA(null);
        settings.setAiExplanationPromptB(null);
        settings.setAiCompletionEnabled(false);
        settings.setAiCompletionPrompt(null);
        return settings;
    }

    private DeckAiSettings normalizeSettings(DeckAiSettings settings) {
        if (settings == null) {
            return createDefaultSettings(null);
        }
        DeckAiSettings defaults = createDefaultSettings(settings.getDeckId());
        if (settings.getAiExplanationEnabledA() == null) {
            settings.setAiExplanationEnabledA(defaults.getAiExplanationEnabledA());
        }
        if (settings.getAiExplanationEnabledB() == null) {
            settings.setAiExplanationEnabledB(defaults.getAiExplanationEnabledB());
        }
        settings.setAiExplanationPromptA(normalizePrompt(settings.getAiExplanationPromptA()));
        settings.setAiExplanationPromptB(normalizePrompt(settings.getAiExplanationPromptB()));
        if (settings.getAiCompletionEnabled() == null) {
            settings.setAiCompletionEnabled(defaults.getAiCompletionEnabled());
        }
        settings.setAiCompletionPrompt(normalizePrompt(settings.getAiCompletionPrompt()));
        return settings;
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null) {
            return null;
        }
        String trimmed = prompt.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 校验保存请求，拦截页面传来的无效卡包或缺少必填字段。 */
    private void validateSaveRequest(Long deckId, DeckAiSettingsUpdateCommand command) {
        if (deckId == null
            || command == null
            || command.aiExplanationEnabledA() == null
            || command.aiExplanationEnabledB() == null
            || command.aiCompletionEnabled() == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
    }

    /** 校验当前用户拥有这个卡包，防止未拥有卡包读取或保存 AI 设置。 */
    private void requireDeckOwnership(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
    }
}
