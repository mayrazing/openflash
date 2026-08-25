package openflash_core.service.impl;

import java.time.LocalDate;
import openflash_core.entity.CardProgress;

final class CardProgressSupport {

    private CardProgressSupport() {
    }

    static CardProgress newProgress(Long cardId, Long userId, String direction) {
        CardProgress progress = new CardProgress();
        resetToNew(progress, cardId, userId, direction);
        return progress;
    }

    static void resetToNew(CardProgress progress, Long cardId, Long userId, String direction) {
        progress.setCardId(cardId);
        progress.setUserId(userId);
        progress.setDirection(direction);
        progress.setState("new");
        progress.setStep(null);
        progress.setStability(0.0);
        progress.setDifficulty(0.0);
        progress.setNextReviewDate(LocalDate.now());
        progress.setLastReviewDate(null);
        progress.setReps(0);
        progress.setLapses(0);
        progress.setLastRating(0);
        progress.setFirstLearnedDate(null);
        progress.setMasteredAt(null);
    }

    static CardProgress copyForDirection(CardProgress source, String direction) {
        CardProgress copy = new CardProgress();
        copy.setCardId(source.getCardId());
        copy.setUserId(source.getUserId());
        copy.setDirection(direction);
        copy.setState(source.getState());
        copy.setStep(source.getStep());
        copy.setStability(source.getStability());
        copy.setDifficulty(source.getDifficulty());
        copy.setNextReviewDate(source.getNextReviewDate());
        copy.setLastReviewDate(source.getLastReviewDate());
        copy.setReps(source.getReps());
        copy.setLapses(source.getLapses());
        copy.setLastRating(source.getLastRating());
        copy.setFirstLearnedDate(source.getFirstLearnedDate());
        copy.setMasteredAt(source.getMasteredAt());
        return copy;
    }
}
