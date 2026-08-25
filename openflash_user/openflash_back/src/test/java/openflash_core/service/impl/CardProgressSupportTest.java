package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import openflash_core.entity.CardProgress;

class CardProgressSupportTest {

    @Test
    void newProgressCreatesDefaultDirectionalProgress() {
        CardProgress progress = CardProgressSupport.newProgress(10L, 20L, "a2b");

        assertNull(progress.getId());
        assertEquals(10L, progress.getCardId());
        assertEquals(20L, progress.getUserId());
        assertEquals("a2b", progress.getDirection());
        assertEquals("new", progress.getState());
        assertNull(progress.getStep());
        assertEquals(0.0, progress.getStability());
        assertEquals(0.0, progress.getDifficulty());
        assertEquals(LocalDate.now(), progress.getNextReviewDate());
        assertNull(progress.getLastReviewDate());
        assertEquals(0, progress.getReps());
        assertEquals(0, progress.getLapses());
        assertEquals(0, progress.getLastRating());
        assertNull(progress.getFirstLearnedDate());
        assertNull(progress.getMasteredAt());
    }

    @Test
    void copyForDirectionCopiesLearningStateWithoutIdentity() {
        CardProgress source = CardProgressSupport.newProgress(10L, 20L, "a2b");
        source.setId(99L);
        source.setState("review");
        source.setStep(1);
        source.setStability(3.5);
        source.setDifficulty(4.5);
        source.setNextReviewDate(LocalDate.of(2026, 4, 24));
        source.setLastReviewDate(LocalDate.of(2026, 4, 23));
        source.setReps(6);
        source.setLapses(1);
        source.setLastRating(3);
        source.setFirstLearnedDate(LocalDate.of(2026, 4, 1));
        source.setMasteredAt(LocalDateTime.of(2026, 4, 24, 10, 0));

        CardProgress copy = CardProgressSupport.copyForDirection(source, "b2a");

        assertNull(copy.getId());
        assertEquals(10L, copy.getCardId());
        assertEquals(20L, copy.getUserId());
        assertEquals("b2a", copy.getDirection());
        assertEquals("review", copy.getState());
        assertEquals(1, copy.getStep());
        assertEquals(3.5, copy.getStability());
        assertEquals(4.5, copy.getDifficulty());
        assertEquals(LocalDate.of(2026, 4, 24), copy.getNextReviewDate());
        assertEquals(LocalDate.of(2026, 4, 23), copy.getLastReviewDate());
        assertEquals(6, copy.getReps());
        assertEquals(1, copy.getLapses());
        assertEquals(3, copy.getLastRating());
        assertEquals(LocalDate.of(2026, 4, 1), copy.getFirstLearnedDate());
        assertEquals(LocalDateTime.of(2026, 4, 24, 10, 0), copy.getMasteredAt());
    }
}
