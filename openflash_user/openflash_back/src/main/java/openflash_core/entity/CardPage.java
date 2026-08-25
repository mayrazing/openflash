package openflash_core.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片分页结果，给卡包详情页做分批加载用。
 */
public class CardPage {

    private List<Card> items = new ArrayList<>();
    private Long total;
    private Integer offset;
    private Integer limit;
    private Boolean hasMore;

    public List<Card> getItems() {
        return items;
    }

    public void setItems(List<Card> items) {
        this.items = items;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
