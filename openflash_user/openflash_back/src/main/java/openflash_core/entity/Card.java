package openflash_core.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Card {

    private Long id;
    private Long deckId;
    private String deckName;
    private String sideA;
    private String sideB;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    private List<String> sideAImage = new ArrayList<>();
    private List<String> sideBImage = new ArrayList<>();
    private String state;
    private CardFsrs fsrs;
    private CardDirectionProgresses directionProgresses;
    private LocalDate firstLearnedDate;
    private LocalDateTime masteredAt;
    private Boolean todayCalculated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeckId() {
        return deckId;
    }

    public void setDeckId(Long deckId) {
        this.deckId = deckId;
    }

    /**
     * 返回卡片所属卡包名称，供跨卡包列表展示来源。
     */
    public String getDeckName() {
        return deckName;
    }

    /**
     * 设置卡片所属卡包名称，避免前端二次拼接卡包映射。
     */
    public void setDeckName(String deckName) {
        this.deckName = deckName;
    }

    public String getSideA() {
        return sideA;
    }

    public void setSideA(String sideA) {
        this.sideA = sideA;
    }

    public String getSideB() {
        return sideB;
    }

    public void setSideB(String sideB) {
        this.sideB = sideB;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public List<String> getSideAImage() {
        return sideAImage;
    }

    public void setSideAImage(List<String> sideAImage) {
        this.sideAImage = sideAImage;
    }

    public List<String> getSideBImage() {
        return sideBImage;
    }

    public void setSideBImage(List<String> sideBImage) {
        this.sideBImage = sideBImage;
    }

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

    public CardDirectionProgresses getDirectionProgresses() {
        return directionProgresses;
    }

    public void setDirectionProgresses(CardDirectionProgresses directionProgresses) {
        this.directionProgresses = directionProgresses;
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

    /**
     * 返回卡片今天是否执行过算法计算。
     */
    public Boolean getTodayCalculated() {
        return todayCalculated;
    }

    /**
     * 设置卡片今天是否执行过算法计算。
     */
    public void setTodayCalculated(Boolean todayCalculated) {
        this.todayCalculated = todayCalculated;
    }
}
