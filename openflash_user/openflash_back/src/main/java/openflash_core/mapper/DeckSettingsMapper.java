package openflash_core.mapper;

import org.apache.ibatis.annotations.Mapper;
import openflash_core.entity.DeckSettings;

@Mapper
public interface DeckSettingsMapper {

    DeckSettings findByDeckId(Long deckId);

    int insert(DeckSettings settings);

    int update(DeckSettings settings);

    int deleteByDeckId(Long deckId);
}
