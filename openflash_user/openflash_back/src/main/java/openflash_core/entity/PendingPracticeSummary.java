package openflash_core.entity;

/**
 * 今日待练习摘要。
 */
public class PendingPracticeSummary {

    private Integer pendingTotal;
    private Integer pendingNew;
    private Integer pendingReview;
    private Integer pendingBacklog = 0;
    private Boolean newCardsPaused = false;
    private Integer targetReviewItemCount = 0;
    private Integer maxReviewItemCount = 0;

    public PendingPracticeSummary() {
    }

    public PendingPracticeSummary(Integer pendingTotal, Integer pendingNew, Integer pendingReview) {
        this(pendingTotal, pendingNew, pendingReview, 0, false, 0, 0);
    }

    public PendingPracticeSummary(
        Integer pendingTotal,
        Integer pendingNew,
        Integer pendingReview,
        Integer pendingBacklog,
        Boolean newCardsPaused,
        Integer targetReviewItemCount,
        Integer maxReviewItemCount
    ) {
        this.pendingTotal = pendingTotal;
        this.pendingNew = pendingNew;
        this.pendingReview = pendingReview;
        this.pendingBacklog = pendingBacklog;
        this.newCardsPaused = newCardsPaused;
        this.targetReviewItemCount = targetReviewItemCount;
        this.maxReviewItemCount = maxReviewItemCount;
    }

    public Integer getPendingTotal() {
        return pendingTotal;
    }

    public void setPendingTotal(Integer pendingTotal) {
        this.pendingTotal = pendingTotal;
    }

    public Integer getPendingNew() {
        return pendingNew;
    }

    public void setPendingNew(Integer pendingNew) {
        this.pendingNew = pendingNew;
    }

    public Integer getPendingReview() {
        return pendingReview;
    }

    public void setPendingReview(Integer pendingReview) {
        this.pendingReview = pendingReview;
    }

    public Integer getPendingBacklog() {
        return pendingBacklog;
    }

    public void setPendingBacklog(Integer pendingBacklog) {
        this.pendingBacklog = pendingBacklog;
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
