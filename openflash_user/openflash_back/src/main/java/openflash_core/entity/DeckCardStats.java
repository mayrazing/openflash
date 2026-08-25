package openflash_core.entity;

/**
 * 卡包详情页顶部统计。
 */
public class DeckCardStats {

    private Integer total;
    private Integer newCount;
    private Integer learningCount;
    private Integer masteredCount;
    private Integer tomorrowCount;
    private Integer todayCount;
    private Integer backlogCount = 0;
    private Boolean newCardsPaused = false;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getNewCount() {
        return newCount;
    }

    public void setNewCount(Integer newCount) {
        this.newCount = newCount;
    }

    public Integer getLearningCount() {
        return learningCount;
    }

    public void setLearningCount(Integer learningCount) {
        this.learningCount = learningCount;
    }

    public Integer getMasteredCount() {
        return masteredCount;
    }

    public void setMasteredCount(Integer masteredCount) {
        this.masteredCount = masteredCount;
    }

    public Integer getTomorrowCount() {
        return tomorrowCount;
    }

    public void setTomorrowCount(Integer tomorrowCount) {
        this.tomorrowCount = tomorrowCount;
    }

    public Integer getTodayCount() {
        return todayCount;
    }

    public void setTodayCount(Integer todayCount) {
        this.todayCount = todayCount;
    }

    public Integer getBacklogCount() {
        return backlogCount;
    }

    public void setBacklogCount(Integer backlogCount) {
        this.backlogCount = backlogCount;
    }

    public Boolean getNewCardsPaused() {
        return newCardsPaused;
    }

    public void setNewCardsPaused(Boolean newCardsPaused) {
        this.newCardsPaused = newCardsPaused;
    }
}
