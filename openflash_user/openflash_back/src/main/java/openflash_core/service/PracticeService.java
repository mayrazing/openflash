package openflash_core.service;

import java.util.List;
import openflash_core.entity.Card;
import openflash_core.entity.CardProgressSnapshot;
import openflash_core.entity.PendingPracticeSummary;
import openflash_core.entity.PracticeModeOption;
import openflash_core.entity.PracticeQueue;
import openflash_core.entity.ProgressUpdateResult;
import openflash_core.dto.ReviewRequest;

/**
 * 负责学习进度与练习队列相关业务。
 */
public interface PracticeService {

    String DEFAULT_MODE = "random";

    /**
     * 构建某个卡包当天的练习队列。
     */
    PracticeQueue buildDailyQueue(Long deckId, Integer newCardsLimit, String mode);

    /**
     * 读取页面可选择的练习模式。
     */
    List<PracticeModeOption> listPracticeModes();

    /**
     * 更新卡片学习进度。
     */
    ProgressUpdateResult updateCardProgress(Long cardId, CardProgressSnapshot snapshot);

    /**
     * 对卡片执行正式评分。
     */
    ProgressUpdateResult reviewCard(Long cardId, ReviewRequest request);

    /**
     * 读取今日待练习摘要。
     */
    PendingPracticeSummary getPendingPracticeSummary(Long deckId, Integer newCardsLimit, String mode);

    /**
     * 读取今日涉及的卡片。
     */
    List<Card> getTodayCardsByDeck(Long deckId, Integer newCardsLimit);

    /**
     * 读取已掌握卡包列表。
     */
    List<Card> listMasteredCards(String keyword);

    /**
     * 将卡片移动到已掌握卡包。
     */
    Card moveToMastered(Long cardId);

    /**
     * 将卡片从已掌握卡包移回待学习状态。
     */
    Card removeFromMastered(Long cardId);

    /**
     * 将卡片标记为已毕业，永久退出复习队列。
     */
    Card moveToGraduated(Long cardId);
}
