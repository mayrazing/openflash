package openflash_core.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import openflash_core.entity.PracticeReviewLoadProfile;
import openflash_core.service.SystemConfigService;

@Component
public record PracticeReviewSchedulerConfig(
    int targetDailyDirections,
    int absoluteDailyDirections,
    int maxDeferralDays,
    int backlogPauseNewThreshold,
    int backlogResumeNewThreshold
) {

    /**
     * 使用数字配置创建调度配置，供测试直接控制每日复习目标。
     */
    public PracticeReviewSchedulerConfig {
        targetDailyDirections = Math.max(1, targetDailyDirections);
        absoluteDailyDirections = Math.max(targetDailyDirections, absoluteDailyDirections);
        maxDeferralDays = Math.max(0, maxDeferralDays);
        backlogPauseNewThreshold = Math.max(0, backlogPauseNewThreshold);
        backlogResumeNewThreshold = Math.min(backlogPauseNewThreshold, Math.max(0, backlogResumeNewThreshold));
    }

    /**
     * 从系统配置读取调度参数，数据库缺失或格式错误时走旧默认值。
     */
    @Autowired
    public PracticeReviewSchedulerConfig(SystemConfigService systemConfigService) {
        this(
            systemConfigService.getInt("practice.review.target-daily-directions", 40),
            systemConfigService.getInt("practice.review.absolute-daily-directions", 70),
            systemConfigService.getInt("practice.review.max-deferral-days", 3),
            systemConfigService.getInt("practice.review.backlog-pause-new-threshold", 120),
            systemConfigService.getInt("practice.review.backlog-resume-new-threshold", 40)
        );
    }

    /**
     * 返回复习调度的默认参数，保持没有数据库配置时的稳定行为。
     */
    public static PracticeReviewSchedulerConfig defaults() {
        return new PracticeReviewSchedulerConfig(40, 70, 3, 120, 40);
    }

    /**
     * 用用户选择的学习强度覆盖每日目标、上限和暂停线，最大延期天数仍沿用系统配置。
     */
    public PracticeReviewSchedulerConfig withLoadProfile(String reviewLoadProfile) {
        if (reviewLoadProfile == null || reviewLoadProfile.isBlank()) {
            return this;
        }
        PracticeReviewLoadProfile profile = PracticeReviewLoadProfile.fromKey(reviewLoadProfile);
        return new PracticeReviewSchedulerConfig(
            profile.targetDailyDirections(),
            profile.absoluteDailyDirections(),
            maxDeferralDays,
            profile.backlogPauseNewThreshold(),
            profile.backlogResumeNewThreshold()
        );
    }
}
