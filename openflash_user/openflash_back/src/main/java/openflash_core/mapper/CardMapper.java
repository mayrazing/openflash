package openflash_core.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.Card;
import openflash_core.entity.DeckLearningStats;
import openflash_core.entity.TopReviewCard;

@Mapper
public interface CardMapper {

    List<Card> findByDeckId(@Param("deckId") Long deckId, @Param("keyword") String keyword);

    List<Card> findPageByDeckId(
        @Param("deckId") Long deckId,
        @Param("keyword") String keyword,
        @Param("state") String state,
        @Param("userId") Long userId,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit,
        @Param("sort") String sort
    );

    Long countByDeckId(
        @Param("deckId") Long deckId,
        @Param("keyword") String keyword,
        @Param("state") String state,
        @Param("userId") Long userId
    );

    DeckLearningStats selectLearningStats(
        @Param("deckId") Long deckId,
        @Param("userId") Long userId,
        @Param("today") java.time.LocalDate today,
        @Param("newCardsLimit") Integer newCardsLimit
    );

    List<TopReviewCard> selectTopReviewCards(
        @Param("deckId") Long deckId,
        @Param("userId") Long userId,
        @Param("limit") Integer limit
    );

    List<Card> findDeduplicationCandidates(
        @Param("deckId") Long deckId,
        @Param("excludingCardId") Long excludingCardId
    );

    Card findById(Long id);

    List<Card> findByIds(@Param("ids") Collection<Long> ids);

    int insert(Card card);

    int updateCard(Card card);

    int updateDeckId(
        @Param("id") Long id,
        @Param("sourceDeckId") Long sourceDeckId,
        @Param("targetDeckId") Long targetDeckId
    );

    int updateSideAIfEmpty(@Param("id") Long id, @Param("value") String value);

    int updateSideBIfEmpty(@Param("id") Long id, @Param("value") String value);

    List<Long> findAllActiveIds();

    int deleteById(Long id);

    int deleteByDeckId(Long deckId);
}
