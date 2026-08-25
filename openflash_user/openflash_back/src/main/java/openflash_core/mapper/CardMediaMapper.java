package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.CardMedia;

@Mapper
public interface CardMediaMapper {

    List<CardMedia> findByCardId(Long cardId);

    List<CardMedia> findByCardIds(@Param("cardIds") List<Long> cardIds);

    int insert(CardMedia cardMedia);

    List<CardMedia> findByDeckId(Long deckId);

    Long lockFirstReferenceIdByOtherUser(
        @Param("relativePath") String relativePath,
        @Param("userId") Long userId);

    int deleteByCardId(Long cardId);

    int deleteByDeckId(Long deckId);
}
