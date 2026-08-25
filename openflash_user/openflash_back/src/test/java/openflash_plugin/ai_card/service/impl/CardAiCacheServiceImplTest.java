package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import openflash_core.config.AsyncTaskProperties;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.mapper.CardAiCacheMapper;

class CardAiCacheServiceImplTest {

    @Test
    void saveReadyUsesSamePromptFingerprintForCaseAndSpaceVariants() {
        RecordingCardAiCacheMapper mapper = new RecordingCardAiCacheMapper();
        CardAiCacheServiceImpl service = new CardAiCacheServiceImpl(mapper, new AsyncTaskProperties());

        service.saveReadyFromBackground(7L, "fp-a", " Apple   Pie ", "first", false);
        String firstFingerprint = mapper.lastPromptFingerprint;
        service.saveReadyFromBackground(7L, "fp-b", "apple pie", "second", false);

        assertEquals(firstFingerprint, mapper.lastPromptFingerprint);
        assertEquals(7L, mapper.lastOwnerUserId);
    }

    private static final class RecordingCardAiCacheMapper implements CardAiCacheMapper {
        private String lastPromptFingerprint;
        private Long lastOwnerUserId;

        @Override
        public CardAiCache findByFingerprint(Long ownerUserId, String fingerprint) {
            return null;
        }

        @Override
        public int saveReady(
                Long ownerUserId,
                String fingerprint,
                String promptFingerprint,
                String prompt,
                String content,
                Boolean thinkUsed,
                LocalDateTime generatedAt,
                LocalDateTime accessedAt) {
            this.lastOwnerUserId = ownerUserId;
            this.lastPromptFingerprint = promptFingerprint;
            return 1;
        }

        @Override
        public int touchAccessedAtIfStale(Long ownerUserId, String fingerprint, LocalDateTime accessedAt, LocalDateTime minEligibleBefore) {
            return 0;
        }

        @Override
        public int deleteExpired(LocalDateTime before, int limit) {
            return 0;
        }

    }
}
