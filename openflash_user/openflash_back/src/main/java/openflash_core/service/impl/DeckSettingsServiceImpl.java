package openflash_core.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.PracticeReviewLoadProfile;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckSettings;
import openflash_core.dto.DeckSettingsUpdateCommand;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckSettingsService;

@Service
public class DeckSettingsServiceImpl implements DeckSettingsService {

    private static final int DEFAULT_NEW_CARDS_PER_DAY = 10;
    private static final int MIN_NEW_CARDS_PER_DAY = 0;
    private static final int MAX_NEW_CARDS_PER_DAY = 50;
    private static final BigDecimal DEFAULT_TARGET_RETENTION = new BigDecimal("0.9000");
    private static final BigDecimal MIN_TARGET_RETENTION = new BigDecimal("0.7000");
    private static final BigDecimal MAX_TARGET_RETENTION = new BigDecimal("0.9700");
    private static final String DEFAULT_REVIEW_LOAD_PROFILE = PracticeReviewLoadProfile.STANDARD.key();

    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;
    private final DeckSettingsMapper deckSettingsMapper;

    public DeckSettingsServiceImpl(
        CurrentUserService currentUserService,
        DeckMapper deckMapper,
        DeckSettingsMapper deckSettingsMapper
    ) {
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
        this.deckSettingsMapper = deckSettingsMapper;
    }

    /**
     * 返回用户打开核心卡包设置页时要显示的卡包设置。
     */
    @Override
    public DeckSettings getSettings(Long deckId) {
        requireDeckOwnership(deckId);
        DeckSettings settings = deckSettingsMapper.findByDeckId(deckId);
        if (settings == null) {
            return defaultSettings(deckId);
        }
        return normalizeSettings(settings);
    }

    /**
     * 保存用户在核心卡包设置页修改的设置，越界值按页面范围收敛。
     */
    @Override
    @Transactional
    public DeckSettings updateSettings(Long deckId, DeckSettingsUpdateCommand command) {
        requireDeckOwnership(deckId);
        requireCompleteSettings(command);
        DeckSettings current = deckSettingsMapper.findByDeckId(deckId);
        boolean isNew = current == null;
        if (isNew) {
            current = defaultSettings(deckId);
        }
        DeckSettings previous = normalizeSettings(current);
        DeckSettings next = defaultSettings(deckId);
        next.setNewCardsPerDay(command.newCardsPerDay());
        next.setTargetRetention(command.targetRetention());
        String trimmed = command.reviewLoadProfile().trim();
        if (!PracticeReviewLoadProfile.isSupported(trimmed)) {
            throw new AppException(ErrorCode.STUDY_INTENSITY_INVALID);
        }
        next.setReviewLoadProfile(trimmed);
        next.setDuplicateSideAEnabled(command.duplicateSideAEnabled());
        next.setDuplicateSideBEnabled(command.duplicateSideBEnabled());
        normalizeSettings(next);
        if (!isNew && sameUserValues(previous, next)) {
            return previous;
        }
        next.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            deckSettingsMapper.insert(next);
        } else {
            deckSettingsMapper.update(next);
        }
        return next;
    }

    private void requireDeckOwnership(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
    }

    private DeckSettings defaultSettings(Long deckId) {
        return createDefaultSettings(deckId);
    }

    @Override
    public DeckSettings createDefaultSettings(Long deckId) {
        DeckSettings settings = new DeckSettings();
        settings.setDeckId(deckId);
        settings.setNewCardsPerDay(DEFAULT_NEW_CARDS_PER_DAY);
        settings.setTargetRetention(DEFAULT_TARGET_RETENTION);
        settings.setReviewLoadProfile(DEFAULT_REVIEW_LOAD_PROFILE);
        settings.setDuplicateSideAEnabled(true);
        settings.setDuplicateSideBEnabled(false);
        return settings;
    }

    @Override
    public DeckSettings createDefaultSettingsForInsert(Long deckId) {
        DeckSettings settings = createDefaultSettings(deckId);
        settings.setUpdatedAt(LocalDateTime.now());
        return settings;
    }

    @Override
    public DeckSettings normalizeSettings(DeckSettings settings) {
        if (settings == null) {
            return createDefaultSettings(null);
        }
        DeckSettings defaults = createDefaultSettings(settings.getDeckId());
        settings.setNewCardsPerDay(clampNewCardsPerDay(
            settings.getNewCardsPerDay() == null ? defaults.getNewCardsPerDay() : settings.getNewCardsPerDay()));
        settings.setTargetRetention(clampTargetRetention(
            settings.getTargetRetention() == null ? defaults.getTargetRetention() : settings.getTargetRetention()));
        settings.setReviewLoadProfile(PracticeReviewLoadProfile.fromKey(settings.getReviewLoadProfile()).key());
        if (settings.getDuplicateSideAEnabled() == null) {
            settings.setDuplicateSideAEnabled(defaults.getDuplicateSideAEnabled());
        }
        if (settings.getDuplicateSideBEnabled() == null) {
            settings.setDuplicateSideBEnabled(defaults.getDuplicateSideBEnabled());
        }
        return settings;
    }

    @Override
    public boolean sameUserValues(DeckSettings left, DeckSettings right) {
        return Objects.equals(left.getNewCardsPerDay(), right.getNewCardsPerDay())
            && compareDecimal(left.getTargetRetention(), right.getTargetRetention())
            && Objects.equals(left.getReviewLoadProfile(), right.getReviewLoadProfile())
            && Objects.equals(left.getDuplicateSideAEnabled(), right.getDuplicateSideAEnabled())
            && Objects.equals(left.getDuplicateSideBEnabled(), right.getDuplicateSideBEnabled());
    }

    private int clampNewCardsPerDay(int value) {
        return Math.max(MIN_NEW_CARDS_PER_DAY, Math.min(MAX_NEW_CARDS_PER_DAY, value));
    }

    private BigDecimal clampTargetRetention(BigDecimal targetRetention) {
        if (targetRetention.compareTo(MIN_TARGET_RETENTION) < 0) {
            return MIN_TARGET_RETENTION;
        }
        if (targetRetention.compareTo(MAX_TARGET_RETENTION) > 0) {
            return MAX_TARGET_RETENTION;
        }
        return targetRetention;
    }

    private boolean compareDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    /**
     * PUT 接口按全量保存处理，缺字段说明请求体不完整。
     */
    private void requireCompleteSettings(DeckSettingsUpdateCommand command) {
        if (command == null
            || command.newCardsPerDay() == null
            || command.targetRetention() == null
            || command.reviewLoadProfile() == null
            || command.duplicateSideAEnabled() == null
            || command.duplicateSideBEnabled() == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
    }

}
