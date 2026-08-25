package openflash_core.entity;

import java.util.List;

public record PracticeReviewSchedule(
    List<Card> reviewCards,
    List<Card> newCards,
    PracticeReviewLoad load
) {

    /**
     * 返回当天未进入复习队列的到期方向数，用于页面判断复习积压。
     */
    public int backlogDirectionCount() {
        return load.backlogDirectionCount();
    }

    /**
     * 返回当天未完全排进复习队列的到期卡片数，用于页面展示卡片口径的积压。
     */
    public int backlogCardCount() {
        return load.backlogCardCount();
    }

    /**
     * 返回新卡是否因复习积压被暂停，用于页面隐藏或置空新卡。
     */
    public boolean newCardsPaused() {
        return load.newCardsPaused();
    }
}
