package openflash_plugin.ai_card.service;

import openflash_plugin.ai_card.entity.CardAiCache;

public interface CardAiCacheService {

    default CardAiCache findUsableCacheAndTouchOnServe(Long ownerUserId, String fingerprint) {
        throw new UnsupportedOperationException();
    }

    default CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
        throw new UnsupportedOperationException();
    }

    default void saveReadyFromServe(Long ownerUserId, String fingerprint, String prompt, String content, Boolean thinkUsed) {
        throw new UnsupportedOperationException();
    }

    default void saveReadyFromBackground(Long ownerUserId, String fingerprint, String prompt, String content, Boolean thinkUsed) {
        throw new UnsupportedOperationException();
    }

    default int deleteExpired(java.time.LocalDateTime before, int limit) {
        return 0;
    }

}
