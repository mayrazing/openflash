package openflash_core.service.impl;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.CardProgress;
import openflash_core.service.FsrsService;

/**
 * 使用 Java FSRS 库执行正式评分调度。
 */
@Service
public class FsrsServiceImpl implements FsrsService {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final Duration[] LEARNING_STEPS = {
        Duration.ofDays(1),
        Duration.ofDays(2)
    };
    private static final Duration[] RELEARNING_STEPS = {
        Duration.ofDays(1)
    };

    /**
     * 根据当前进度与评分执行调度，并生成一份新的进度快照。
     */
    @Override
    public CardProgress schedule(CardProgress currentProgress, Integer rating, Double targetRetention) {
        if (currentProgress == null) {
            throw new AppException(ErrorCode.PRACTICE_STATE_INVALID);
        }
        Rating mappedRating = mapRating(rating);
        Scheduler scheduler = Scheduler.builder()
            .desiredRetention(normalizeRetention(targetRetention))
            .learningSteps(LEARNING_STEPS)
            .relearningSteps(RELEARNING_STEPS)
            .build();

        Instant now = Instant.now();
        CardAndReviewLog result = scheduler.reviewCard(toFsrsCard(currentProgress), mappedRating, now);
        Card scheduled = result.card();

        CardProgress next = new CardProgress();
        next.setCardId(currentProgress.getCardId());
        next.setUserId(currentProgress.getUserId());
        next.setState(mapState(scheduled.getState()));
        next.setStep(scheduled.getStep());
        next.setStability(defaultDouble(scheduled.getStability()));
        next.setDifficulty(defaultDouble(scheduled.getDifficulty()));
        next.setNextReviewDate(toLocalDate(scheduled.getDue()));
        next.setLastReviewDate(toLocalDate(now));
        next.setReps(safeInt(currentProgress.getReps()) + 1);
        next.setLapses(calculateLapses(currentProgress, scheduled.getState(), mappedRating));
        next.setLastRating(rating);
        next.setFirstLearnedDate(resolveFirstLearnedDate(currentProgress));
        next.setMasteredAt(currentProgress.getMasteredAt());
        return next;
    }

    private Card toFsrsCard(CardProgress progress) {
        if (safeInt(progress.getReps()) == 0) {
            return Card.builder()
                .cardId(Math.toIntExact(progress.getCardId()))
                .build();
        }

        Card.Builder builder = Card.builder()
            .cardId(Math.toIntExact(progress.getCardId()))
            .state(mapState(progress.getState()))
            .step(progress.getStep())
            .stability(defaultDouble(progress.getStability()))
            .difficulty(defaultDouble(progress.getDifficulty()))
            .due(toInstant(progress.getNextReviewDate()))
            .lastReview(toInstant(progress.getLastReviewDate()));

        return builder.build();
    }

    private Rating mapRating(Integer rating) {
        if (rating == null) {
            throw new AppException(ErrorCode.PRACTICE_RATING_INVALID);
        }
        return switch (rating) {
            case 0 -> Rating.AGAIN;
            case 1 -> Rating.HARD;
            case 2 -> Rating.GOOD;
            case 3 -> Rating.EASY;
            default -> throw new AppException(ErrorCode.PRACTICE_RATING_INVALID);
        };
    }

    private State mapState(String state) {
        if (state == null || state.isBlank() || "new".equals(state)) {
            return null;
        }
        return switch (state) {
            case "learning" -> State.LEARNING;
            case "review" -> State.REVIEW;
            case "relearning" -> State.RELEARNING;
            default -> throw new AppException(ErrorCode.PRACTICE_STATE_INVALID);
        };
    }

    private String mapState(State state) {
        if (state == null) {
            return "new";
        }
        return switch (state) {
            case LEARNING -> "learning";
            case REVIEW -> "review";
            case RELEARNING -> "relearning";
        };
    }

    private int calculateLapses(CardProgress currentProgress, State nextState, Rating rating) {
        int base = safeInt(currentProgress.getLapses());
        State currentState = mapState(currentProgress.getState());
        if (rating == Rating.AGAIN && currentState == State.REVIEW && nextState == State.RELEARNING) {
            return base + 1;
        }
        return base;
    }

    private LocalDate resolveFirstLearnedDate(CardProgress currentProgress) {
        if (currentProgress.getFirstLearnedDate() != null) {
            return currentProgress.getFirstLearnedDate();
        }
        return LocalDate.now();
    }

    private double normalizeRetention(Double targetRetention) {
        if (targetRetention == null) {
            return 0.9;
        }
        return targetRetention;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private Instant toInstant(LocalDate date) {
        if (date == null) {
            return Instant.now();
        }
        return date.atStartOfDay(SYSTEM_ZONE).toInstant();
    }

    /**
     * 按服务端本地时区把时间戳转换成日期，避免跨天错位。
     */
    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(SYSTEM_ZONE).toLocalDate();
    }
}
