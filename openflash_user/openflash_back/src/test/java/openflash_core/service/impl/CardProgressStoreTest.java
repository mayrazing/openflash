package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.B_TO_A;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import openflash_core.entity.CardProgress;
import openflash_core.mapper.CardProgressMapper;

class CardProgressStoreTest {

    @Test
    void concurrentEnsureFromEmptyRowsReturnsBothDirectionsWithoutDuplicateKeyFailure() throws Exception {
        RacingCardProgressMapper mapper = new RacingCardProgressMapper();
        CardProgressStore store = new CardProgressStore(mapper);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<CardProgress>> first = executor.submit(() -> store.ensureDirectionalProgressRows(1L, 10L));
            Future<List<CardProgress>> second = executor.submit(() -> store.ensureDirectionalProgressRows(1L, 10L));

            List<CardProgress> firstProgresses = get(first);
            List<CardProgress> secondProgresses = get(second);

            assertBothDirections(firstProgresses);
            assertBothDirections(secondProgresses);
            assertBothDirections(mapper.findByUserIdAndCardId(1L, 10L));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static List<CardProgress> get(Future<List<CardProgress>> future) throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError("Concurrent ensure should not fail", exception.getCause());
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new AssertionError("Timed out waiting for concurrent ensure", exception);
        }
    }

    private static void assertBothDirections(List<CardProgress> progresses) {
        assertEquals(2, progresses.size());
        assertEquals(List.of(A_TO_B, B_TO_A), progresses.stream()
            .map(CardProgress::getDirection)
            .sorted()
            .toList());
    }

    private static final class RacingCardProgressMapper implements CardProgressMapper {
        private final List<CardProgress> rows = new ArrayList<>();
        private final AtomicInteger findCalls = new AtomicInteger();
        private final CountDownLatch concurrentFinds = new CountDownLatch(2);
        private long nextId = 1L;

        @Override
        public CardProgress findByUserIdAndCardIdAndDirection(Long userId, Long cardId, String direction) {
            return findByUserIdAndCardId(userId, cardId).stream()
                .filter(progress -> direction.equals(progress.getDirection()))
                .findFirst()
                .orElse(null);
        }

        @Override
        public List<CardProgress> findByUserIdAndCardId(Long userId, Long cardId) {
            int call = findCalls.incrementAndGet();
            if (call <= 2) {
                concurrentFinds.countDown();
                awaitBothInitialFinds();
            }

            return snapshotRows(userId, cardId);
        }

        /** 测试替身：FOR UPDATE 锁定读直接返回最新行，不参与初次并发读的 latch。 */
        @Override
        public List<CardProgress> findByUserIdAndCardIdForUpdate(Long userId, Long cardId) {
            return snapshotRows(userId, cardId);
        }

        private List<CardProgress> snapshotRows(Long userId, Long cardId) {
            synchronized (rows) {
                return rows.stream()
                    .filter(progress -> userId.equals(progress.getUserId()) && cardId.equals(progress.getCardId()))
                    .sorted(Comparator.comparing(CardProgress::getDirection))
                    .map(this::copy)
                    .toList();
            }
        }

        @Override
        public List<CardProgress> findByUserIdAndCardIds(Long userId, List<Long> cardIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findMasteredCardIds(Long userId, String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(CardProgress cardProgress) {
            synchronized (rows) {
                boolean duplicate = rows.stream().anyMatch(existing ->
                    existing.getUserId().equals(cardProgress.getUserId())
                        && existing.getCardId().equals(cardProgress.getCardId())
                        && existing.getDirection().equals(cardProgress.getDirection()));
                if (duplicate) {
                    throw new DuplicateKeyException("Duplicate progress direction");
                }

                CardProgress stored = copy(cardProgress);
                stored.setId(nextId++);
                rows.add(stored);
                cardProgress.setId(stored.getId());
                return 1;
            }
        }

        @Override
        public int updateByUserIdAndCardIdAndDirection(CardProgress cardProgress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByCardId(Long cardId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByDeckId(Long deckId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByUserId(Long userId) {
            throw new UnsupportedOperationException();
        }

        /** 测试替身返回今天已复习方向数。 */
        @Override
        public int countReviewedDirectionsToday(Long userId, LocalDate today, Long deckId) {
            return 0;
        }

        private void awaitBothInitialFinds() {
            try {
                if (!concurrentFinds.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for initial concurrent reads");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for initial concurrent reads", exception);
            }
        }

        private CardProgress copy(CardProgress source) {
            CardProgress copy = new CardProgress();
            copy.setId(source.getId());
            copy.setCardId(source.getCardId());
            copy.setUserId(source.getUserId());
            copy.setDirection(source.getDirection());
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
            copy.setUpdatedAt(source.getUpdatedAt());
            return copy;
        }
    }
}
