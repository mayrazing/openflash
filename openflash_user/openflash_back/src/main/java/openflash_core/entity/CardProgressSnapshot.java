package openflash_core.entity;

/**
 * 用于前后端传递整张卡片的双方向学习进度快照。
 */
public class CardProgressSnapshot {

    private String itemKey;
    private String direction;
    private CardDirectionProgresses directionProgresses;

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public CardDirectionProgresses getDirectionProgresses() {
        return directionProgresses;
    }

    public void setDirectionProgresses(CardDirectionProgresses directionProgresses) {
        this.directionProgresses = directionProgresses;
    }
}
