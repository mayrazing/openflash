package openflash_core.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表示某个练习方向的学习进度快照。
 */
public class DirectionProgressSnapshot {

    private String state;
    private CardFsrs fsrs;
    private LocalDate firstLearnedDate;
    private LocalDateTime masteredAt;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public CardFsrs getFsrs() {
        return fsrs;
    }

    public void setFsrs(CardFsrs fsrs) {
        this.fsrs = fsrs;
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
}
