package openflash_plugin.ai_card.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_plugin.ai_card.entity.CardAiCache;

@Mapper
public interface CardAiCacheMapper {

    CardAiCache findByFingerprint(
        @Param("ownerUserId") Long ownerUserId,
        @Param("fingerprint") String fingerprint
    );

    int saveReady(
        @Param("ownerUserId") Long ownerUserId,
        @Param("fingerprint") String fingerprint,
        @Param("promptFingerprint") String promptFingerprint,
        @Param("prompt") String prompt,
        @Param("content") String content,
        @Param("thinkUsed") Boolean thinkUsed,
        @Param("generatedAt") LocalDateTime generatedAt,
        @Param("accessedAt") LocalDateTime accessedAt
    );

    int touchAccessedAtIfStale(
        @Param("ownerUserId") Long ownerUserId,
        @Param("fingerprint") String fingerprint,
        @Param("accessedAt") LocalDateTime accessedAt,
        @Param("minEligibleBefore") LocalDateTime minEligibleBefore
    );

    int deleteExpired(
        @Param("before") LocalDateTime before,
        @Param("limit") int limit
    );
}
