package openflash_core.service.impl;

import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.B_TO_A;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import openflash_core.entity.CardProgress;
import openflash_core.mapper.CardProgressMapper;

@Component
class CardProgressStore {

    private final CardProgressMapper cardProgressMapper;

    CardProgressStore(CardProgressMapper cardProgressMapper) {
        this.cardProgressMapper = cardProgressMapper;
    }

    List<CardProgress> ensureDirectionalProgressRows(Long userId, Long cardId) {
        List<CardProgress> progresses = new ArrayList<>(cardProgressMapper.findByUserIdAndCardId(userId, cardId));
        CardProgress a2bProgress = findDirectionProgress(progresses, A_TO_B);
        CardProgress b2aProgress = findDirectionProgress(progresses, B_TO_A);
        if (a2bProgress != null && b2aProgress != null) {
            return progresses;
        }

        if (a2bProgress == null) {
            a2bProgress = buildMissingProgress(cardId, userId, A_TO_B, b2aProgress);
            insertIfAbsent(a2bProgress);
        }
        if (b2aProgress == null) {
            insertIfAbsent(buildMissingProgress(cardId, userId, B_TO_A, a2bProgress));
        }

        // 用锁定读回读：本方法可能并发首访同卡，输家 insert 撞唯一键被 insertIfAbsent 吞掉，
        // 普通快照读在 REPEATABLE READ 下看不见赢家刚提交的行 → 返回空 → 调用方 get(0) 崩。
        // FOR UPDATE 读最新已提交版本，保证非空。
        return cardProgressMapper.findByUserIdAndCardIdForUpdate(userId, cardId);
    }

    private CardProgress findDirectionProgress(List<CardProgress> progresses, String direction) {
        return progresses.stream()
            .filter(progress -> Objects.equals(direction, progress.getDirection()))
            .findFirst()
            .orElse(null);
    }

    private CardProgress buildMissingProgress(Long cardId, Long userId, String direction, CardProgress sibling) {
        if (sibling == null) {
            return CardProgressSupport.newProgress(cardId, userId, direction);
        }

        CardProgress progress = CardProgressSupport.copyForDirection(sibling, direction);
        progress.setId(null);
        return progress;
    }

    private void insertIfAbsent(CardProgress progress) {
        try {
            cardProgressMapper.insert(progress);
        } catch (DuplicateKeyException ignored) {
            // 并发请求可能已在初次读取后写入同方向进度。
        }
    }

    /**
     * 为新卡片写入默认学习进度。
     */
    void createDefaultProgressRows(Long cardId, Long userId) {
        cardProgressMapper.insert(CardProgressSupport.newProgress(cardId, userId, A_TO_B));
        cardProgressMapper.insert(CardProgressSupport.newProgress(cardId, userId, B_TO_A));
    }

    /**
     * 不存在时返回未保存的默认进度，不写库。
     */
    CardProgress getOrCreateProgress(Long userId, Long cardId, String direction) {
        CardProgress progress = cardProgressMapper.findByUserIdAndCardIdAndDirection(userId, cardId, direction);
        if (progress != null) {
            return progress;
        }

        CardProgress empty = CardProgressSupport.newProgress(cardId, userId, direction);
        empty.setId(null);
        return empty;
    }
}
