package openflash_core.entity;

import java.util.List;

/**
 * 每日练习队列，由后端直接给出权威 PracticeItem。
 */
public class PracticeQueue {

    private List<PracticeItem> items;
    private Integer reviewItemCount;
    private Integer newItemCount;
    private Integer reviewCardCount;
    private Integer newCardCount;
    private Integer totalCardCount;
    private Integer reviewBacklogCount = 0;
    private Boolean newCardsPaused = false;
    private Integer targetReviewItemCount = 0;
    private Integer maxReviewItemCount = 0;

    public PracticeQueue() {
    }

    public PracticeQueue(
        List<PracticeItem> items,
        Integer reviewItemCount,
        Integer newItemCount,
        Integer reviewCardCount,
        Integer newCardCount,
        Integer totalCardCount
    ) {
        this(
            items,
            reviewItemCount,
            newItemCount,
            reviewCardCount,
            newCardCount,
            totalCardCount,
            0,
            false,
            0,
            0
        );
    }

    public PracticeQueue(
        List<PracticeItem> items,
        Integer reviewItemCount,
        Integer newItemCount,
        Integer reviewCardCount,
        Integer newCardCount,
        Integer totalCardCount,
        Integer reviewBacklogCount,
        Boolean newCardsPaused,
        Integer targetReviewItemCount,
        Integer maxReviewItemCount
    ) {
        this.items = items;
        this.reviewItemCount = reviewItemCount;
        this.newItemCount = newItemCount;
        this.reviewCardCount = reviewCardCount;
        this.newCardCount = newCardCount;
        this.totalCardCount = totalCardCount;
        this.reviewBacklogCount = reviewBacklogCount;
        this.newCardsPaused = newCardsPaused;
        this.targetReviewItemCount = targetReviewItemCount;
        this.maxReviewItemCount = maxReviewItemCount;
    }

    public List<PracticeItem> getItems() {
        return items;
    }

    public void setItems(List<PracticeItem> items) {
        this.items = items;
    }

    public Integer getReviewItemCount() {
        return reviewItemCount;
    }

    public void setReviewItemCount(Integer reviewItemCount) {
        this.reviewItemCount = reviewItemCount;
    }

    public Integer getNewItemCount() {
        return newItemCount;
    }

    public void setNewItemCount(Integer newItemCount) {
        this.newItemCount = newItemCount;
    }

    public Integer getReviewCardCount() {
        return reviewCardCount;
    }

    public void setReviewCardCount(Integer reviewCardCount) {
        this.reviewCardCount = reviewCardCount;
    }

    public Integer getNewCardCount() {
        return newCardCount;
    }

    public void setNewCardCount(Integer newCardCount) {
        this.newCardCount = newCardCount;
    }

    public Integer getTotalCardCount() {
        return totalCardCount;
    }

    public void setTotalCardCount(Integer totalCardCount) {
        this.totalCardCount = totalCardCount;
    }

    public Integer getReviewBacklogCount() {
        return reviewBacklogCount;
    }

    public void setReviewBacklogCount(Integer reviewBacklogCount) {
        this.reviewBacklogCount = reviewBacklogCount;
    }

    public Boolean getNewCardsPaused() {
        return newCardsPaused;
    }

    public void setNewCardsPaused(Boolean newCardsPaused) {
        this.newCardsPaused = newCardsPaused;
    }

    public Integer getTargetReviewItemCount() {
        return targetReviewItemCount;
    }

    public void setTargetReviewItemCount(Integer targetReviewItemCount) {
        this.targetReviewItemCount = targetReviewItemCount;
    }

    public Integer getMaxReviewItemCount() {
        return maxReviewItemCount;
    }

    public void setMaxReviewItemCount(Integer maxReviewItemCount) {
        this.maxReviewItemCount = maxReviewItemCount;
    }
}
