package openflash_core.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DeckSettings {

    private Long id;
    private Long deckId;
    private Integer newCardsPerDay;
    private BigDecimal targetRetention;
    private String reviewLoadProfile;
    private Boolean duplicateSideAEnabled;
    private Boolean duplicateSideBEnabled;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeckId() { return deckId; }
    public void setDeckId(Long deckId) { this.deckId = deckId; }

    public Integer getNewCardsPerDay() { return newCardsPerDay; }
    public void setNewCardsPerDay(Integer newCardsPerDay) { this.newCardsPerDay = newCardsPerDay; }

    public BigDecimal getTargetRetention() { return targetRetention; }
    public void setTargetRetention(BigDecimal targetRetention) { this.targetRetention = targetRetention; }

    public String getReviewLoadProfile() { return reviewLoadProfile; }
    public void setReviewLoadProfile(String reviewLoadProfile) { this.reviewLoadProfile = reviewLoadProfile; }

    public Boolean getDuplicateSideAEnabled() { return duplicateSideAEnabled; }
    public void setDuplicateSideAEnabled(Boolean duplicateSideAEnabled) { this.duplicateSideAEnabled = duplicateSideAEnabled; }

    public Boolean getDuplicateSideBEnabled() { return duplicateSideBEnabled; }
    public void setDuplicateSideBEnabled(Boolean duplicateSideBEnabled) { this.duplicateSideBEnabled = duplicateSideBEnabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
