package openflash_core.dto;

/**
 * 正式评分请求，由后端执行 FSRS 调度。
 */
public class ReviewRequest {

    private String itemKey;
    private String direction;
    private Integer rating;
    /**
     * 只为兼容旧版前端保留. 正式评分会忽略请求值,改读卡包数据库设置.
     */
    private Double targetRetention;

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

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Double getTargetRetention() {
        return targetRetention;
    }

    public void setTargetRetention(Double targetRetention) {
        this.targetRetention = targetRetention;
    }
}
