package openflash_core.entity;

import java.util.ArrayList;
import java.util.List;

public class DeckLearningStats {

    private Integer total;
    private Integer mastered;
    private Integer pendingTotal;
    private Integer pendingNew;
    private Integer pendingReview;
    private Integer todayCompletedNew = 0;
    private Integer todayCompletedReview = 0;
    private Integer backlogCount = 0;
    private Boolean newCardsPaused = false;
    private List<TopReviewCard> topCards = new ArrayList<>();

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getMastered() {
        return mastered;
    }

    public void setMastered(Integer mastered) {
        this.mastered = mastered;
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

    public Integer getTodayCompletedNew() {
        return todayCompletedNew;
    }

    public void setTodayCompletedNew(Integer todayCompletedNew) {
        this.todayCompletedNew = todayCompletedNew;
    }

    public Integer getTodayCompletedReview() {
        return todayCompletedReview;
    }

    public void setTodayCompletedReview(Integer todayCompletedReview) {
        this.todayCompletedReview = todayCompletedReview;
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

    public List<TopReviewCard> getTopCards() {
        return topCards;
    }

    public void setTopCards(List<TopReviewCard> topCards) {
        this.topCards = topCards;
    }
}
