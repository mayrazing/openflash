package openflash_plugin.tts.mapper;

import org.apache.ibatis.annotations.Mapper;
import openflash_plugin.tts.entity.TtsDeckSettings;

@Mapper
public interface TtsDeckSettingsMapper {

    TtsDeckSettings findByDeckId(Long deckId);

    int insert(TtsDeckSettings settings);

    int update(TtsDeckSettings settings);

    int deleteByDeckId(Long deckId);
}
