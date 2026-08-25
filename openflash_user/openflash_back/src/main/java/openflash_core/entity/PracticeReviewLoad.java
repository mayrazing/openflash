package openflash_core.entity;

public record PracticeReviewLoad(
    int selectedReviewDirectionCount,
    int backlogDirectionCount,
    int backlogCardCount,
    boolean newCardsPaused,
    int targetReviewItemCount,
    int maxReviewItemCount
) {
}
