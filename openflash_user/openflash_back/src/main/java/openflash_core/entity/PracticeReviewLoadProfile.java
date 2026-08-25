package openflash_core.entity;

/**
 * 定义用户可选择的复习负载档位，把页面档位转换成调度器实际使用的压力参数。
 */
public enum PracticeReviewLoadProfile {
    RELAXED("relaxed", "轻松", 30, 45, 30, 20, 1),
    STANDARD("standard", "标准", 40, 70, 60, 40, 2),
    INTENSIVE("intensive", "强化", 60, 90, 120, 80, 3);

    private final String key;
    private final String label;
    private final int targetDailyDirections;
    private final int absoluteDailyDirections;
    private final int backlogPauseNewThreshold;
    private final int backlogResumeNewThreshold;
    private final int sortOrder;

    PracticeReviewLoadProfile(
        String key,
        String label,
        int targetDailyDirections,
        int absoluteDailyDirections,
        int backlogPauseNewThreshold,
        int backlogResumeNewThreshold,
        int sortOrder
    ) {
        this.key = key;
        this.label = label;
        this.targetDailyDirections = targetDailyDirections;
        this.absoluteDailyDirections = absoluteDailyDirections;
        this.backlogPauseNewThreshold = backlogPauseNewThreshold;
        this.backlogResumeNewThreshold = backlogResumeNewThreshold;
        this.sortOrder = sortOrder;
    }

    /**
     * 按数据库保存的 key 返回档位；未知 key 回退到标准档，避免配置错误影响练习入口。
     */
    public static PracticeReviewLoadProfile fromKey(String key) {
        for (PracticeReviewLoadProfile profile : values()) {
            if (profile.key.equals(key)) {
                return profile;
            }
        }
        return STANDARD;
    }

    /**
     * 判断 key 是否是系统已实现的复习负载档位。
     */
    public static boolean isSupported(String key) {
        for (PracticeReviewLoadProfile profile : values()) {
            if (profile.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int targetDailyDirections() {
        return targetDailyDirections;
    }

    public int absoluteDailyDirections() {
        return absoluteDailyDirections;
    }

    public int backlogPauseNewThreshold() {
        return backlogPauseNewThreshold;
    }

    public int backlogResumeNewThreshold() {
        return backlogResumeNewThreshold;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
