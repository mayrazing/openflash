package openflash_plugin.ai_card.service;

import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand;

/** 读写卡包级 AI 提示词设置。 */
public interface DeckAiSettingsService {

    /** 返回卡包的 AI 设置；无行时返回默认值（全部开关开启，提示词均为 null）。 */
    DeckAiSettings getByDeckId(Long deckId);

    /** 返回当前用户拥有的卡包 AI 设置；卡包不存在或不属于当前用户时抛出不存在错误。 */
    default DeckAiSettings getForCurrentUser(Long deckId) {
        return getByDeckId(deckId);
    }

    /** 保存卡包 AI 设置；shared 模式自动将 promptA 值同步到 promptB。 */
    DeckAiSettings save(Long deckId, DeckAiSettingsUpdateCommand command);

    /** 保存当前用户拥有的卡包 AI 设置；卡包不存在或不属于当前用户时抛出不存在错误。 */
    default DeckAiSettings saveForCurrentUser(Long deckId, DeckAiSettingsUpdateCommand command) {
        return save(deckId, command);
    }
}
