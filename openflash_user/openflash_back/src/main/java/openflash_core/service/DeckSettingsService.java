package openflash_core.service;

import openflash_core.entity.DeckSettings;
import openflash_core.dto.DeckSettingsUpdateCommand;

public interface DeckSettingsService {

    DeckSettings getSettings(Long deckId);

    DeckSettings createDefaultSettings(Long deckId);

    DeckSettings createDefaultSettingsForInsert(Long deckId);

    DeckSettings normalizeSettings(DeckSettings settings);

    boolean sameUserValues(DeckSettings left, DeckSettings right);

    /**
     * 保存卡包设置页的全量设置：每日新卡、学习强度、目标留存、朗读和去重。
     */
    DeckSettings updateSettings(Long deckId, DeckSettingsUpdateCommand command);
}
