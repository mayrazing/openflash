package openflash_core.controller;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.Card;
import openflash_core.entity.CardProgressSnapshot;
import openflash_core.entity.PendingPracticeSummary;
import openflash_core.entity.PracticeModeOption;
import openflash_core.entity.PracticeQueue;
import openflash_core.entity.ProgressUpdateResult;
import openflash_core.dto.ReviewRequest;
import openflash_core.entity.ResponseTimeConfig;
import openflash_core.service.PracticeService;
import openflash_core.service.SystemConfigService;

/**
 * 处理学习进度与练习流程相关接口。
 */
@RestController
@RequestMapping("/api")
public class PracticeController {

    private final PracticeService practiceService;
    private final SystemConfigService systemConfigService;

    public PracticeController(PracticeService practiceService, SystemConfigService systemConfigService) {
        this.practiceService = practiceService;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 构建某个卡包当天的练习队列。
     */
    @GetMapping("/decks/{deckId}/practice/queue")
    public ApiResponse<PracticeQueue> buildDailyQueue(
        @PathVariable Long deckId,
        @RequestParam(required = false) Integer newCardsLimit,
        @RequestParam(required = false) String mode
    ) {
        return ApiResponse.success(practiceService.buildDailyQueue(deckId, newCardsLimit, mode));
    }

    /**
     * 查询当前可用的练习模式。
     */
    @GetMapping("/practice/modes")
    public ApiResponse<List<PracticeModeOption>> listPracticeModes() {
        return ApiResponse.success(practiceService.listPracticeModes());
    }

    /**
     * 返回练习反应时间阈值配置，供前端静默计时和降档判断使用。
     */
    @GetMapping("/practice/response-time-config")
    public ApiResponse<ResponseTimeConfig> getResponseTimeConfig() {
        int timeout = systemConfigService.getInt("practice.response-time.timeout-seconds", 60);
        int grade3 = systemConfigService.getInt("practice.response-time.grade3-slow-threshold-seconds", 5);
        int grade2 = systemConfigService.getInt("practice.response-time.grade2-slow-threshold-seconds", 10);
        return ApiResponse.success(new ResponseTimeConfig(timeout, grade3, grade2));
    }

    /**
     * 保存卡片学习进度。
     */
    @PutMapping("/cards/{cardId}/progress")
    public ApiResponse<ProgressUpdateResult> updateCardProgress(
        @PathVariable Long cardId,
        @RequestBody CardProgressSnapshot snapshot
    ) {
        return ApiResponse.success(practiceService.updateCardProgress(cardId, snapshot));
    }

    /**
     * 对卡片执行正式评分。
     */
    @PostMapping("/cards/{cardId}/reviews")
    public ApiResponse<ProgressUpdateResult> reviewCard(
        @PathVariable Long cardId,
        @RequestBody ReviewRequest request
    ) {
        return ApiResponse.success(practiceService.reviewCard(cardId, request));
    }

    /**
     * 查询今日待练习摘要。
     */
    @GetMapping("/decks/{deckId}/practice/summary")
    public ApiResponse<PendingPracticeSummary> getPendingPracticeSummary(
        @PathVariable Long deckId,
        @RequestParam(required = false) Integer newCardsLimit,
        @RequestParam(required = false) String mode
    ) {
        return ApiResponse.success(practiceService.getPendingPracticeSummary(deckId, newCardsLimit, mode));
    }

    /**
     * 查询今日相关卡片。
     */
    @GetMapping("/decks/{deckId}/today-cards")
    public ApiResponse<List<Card>> getTodayCardsByDeck(
        @PathVariable Long deckId,
        @RequestParam(required = false) Integer newCardsLimit
    ) {
        return ApiResponse.success(practiceService.getTodayCardsByDeck(deckId, newCardsLimit));
    }

    /**
     * 查询已掌握卡包。
     */
    @GetMapping("/cards/mastered")
    public ApiResponse<List<Card>> listMasteredCards(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(practiceService.listMasteredCards(keyword));
    }

    /**
     * 将卡片标记为已掌握。
     */
    @PostMapping("/cards/{cardId}/mastered")
    public ApiResponse<Card> moveToMastered(@PathVariable Long cardId) {
        return ApiResponse.success(practiceService.moveToMastered(cardId));
    }

    /**
     * 将卡片从已掌握状态恢复。
     */
    @DeleteMapping("/cards/{cardId}/mastered")
    public ApiResponse<Card> removeFromMastered(@PathVariable Long cardId) {
        return ApiResponse.success(practiceService.removeFromMastered(cardId));
    }
}
