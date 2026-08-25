package openflash_core.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.CardProgress;

@Mapper
public interface CardProgressMapper {

    CardProgress findByUserIdAndCardIdAndDirection(
        @Param("userId") Long userId,
        @Param("cardId") Long cardId,
        @Param("direction") String direction
    );

    List<CardProgress> findByUserIdAndCardId(@Param("userId") Long userId, @Param("cardId") Long cardId);

    /**
     * 锁定读取某卡双方向进度。仅用于确保插入后的回读：REPEATABLE READ 下普通快照读看不见并发事务刚提交的行，
     * FOR UPDATE 读取最新已提交版本，避免回读得到空列表。
     */
    List<CardProgress> findByUserIdAndCardIdForUpdate(@Param("userId") Long userId, @Param("cardId") Long cardId);

    List<CardProgress> findByUserIdAndCardIds(@Param("userId") Long userId, @Param("cardIds") List<Long> cardIds);

    List<Long> findMasteredCardIds(@Param("userId") Long userId, @Param("keyword") String keyword);

    int insert(CardProgress cardProgress);

    int updateByUserIdAndCardIdAndDirection(CardProgress cardProgress);

    int deleteByCardId(Long cardId);

    int deleteByDeckId(Long deckId);

    int deleteByUserId(@Param("userId") Long userId);

    /** 统计用户今天在指定卡包内已复习过的卡片方向数量。 */
    int countReviewedDirectionsToday(
        @Param("userId") Long userId,
        @Param("today") LocalDate today,
        @Param("deckId") Long deckId
    );
}
