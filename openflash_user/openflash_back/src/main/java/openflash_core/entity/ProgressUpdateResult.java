package openflash_core.entity;

/**
 * 学习进度更新结果，包含更新后的卡片和掌握判定。
 */
public class ProgressUpdateResult {

    private Card card;
    private Boolean mastered;
    private Boolean graduated;

    public ProgressUpdateResult() {
    }

    public ProgressUpdateResult(Card card, Boolean mastered) {
        this.card = card;
        this.mastered = mastered;
        this.graduated = false;
    }

    public ProgressUpdateResult(Card card, Boolean mastered, Boolean graduated) {
        this.card = card;
        this.mastered = mastered;
        this.graduated = graduated;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public Boolean getMastered() {
        return mastered;
    }

    public void setMastered(Boolean mastered) {
        this.mastered = mastered;
    }

    public Boolean getGraduated() {
        return graduated;
    }

    public void setGraduated(Boolean graduated) {
        this.graduated = graduated;
    }
}
