package openflash_plugin.ai_card.mapper;

import org.apache.ibatis.annotations.Mapper;
import openflash_plugin.ai_card.entity.DeckAiSettings;

@Mapper
public interface DeckAiSettingsMapper {

    /** 按 deck_id 查找设置行；无行返回 null。 */
    DeckAiSettings findByDeckId(Long deckId);

    /** 插入或更新卡包 AI 设置。 */
    int upsert(DeckAiSettings settings);

    /** 删除某个卡包的 AI 设置。 */
    int deleteByDeckId(Long deckId);
}
