package openflash_core.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CardProgress {

    private Long id;
    private Long cardId;
    private Long userId;
    private String direction;
    private String state;
    private Integer step;
    private Double stability;
    private Double difficulty;
    private LocalDate nextReviewDate;
    private LocalDate lastReviewDate;
    private Integer reps;
    private Integer lapses;
    private Integer lastRating;
    private LocalDate firstLearnedDate;
    private LocalDateTime masteredAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public Double getStability() {
        return stability;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public LocalDate getLastReviewDate() {
        return lastReviewDate;
    }

    public void setLastReviewDate(LocalDate lastReviewDate) {
        this.lastReviewDate = lastReviewDate;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Integer getLapses() {
        return lapses;
    }

    public void setLapses(Integer lapses) {
        this.lapses = lapses;
    }

    public Integer getLastRating() {
        return lastRating;
    }

    public void setLastRating(Integer lastRating) {
        this.lastRating = lastRating;
    }

    public LocalDate getFirstLearnedDate() {
        return firstLearnedDate;
    }

    public void setFirstLearnedDate(LocalDate firstLearnedDate) {
        this.firstLearnedDate = firstLearnedDate;
    }

    public LocalDateTime getMasteredAt() {
        return masteredAt;
    }

    public void setMasteredAt(LocalDateTime masteredAt) {
        this.masteredAt = masteredAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
