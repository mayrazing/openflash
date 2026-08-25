package openflash_core.entity;

import java.util.ArrayList;
import java.util.List;

public class CardBatchCreateResult {

    private Integer createdCount;
    private Integer duplicateCount;
    private Integer invalidCount;
    private List<CardBatchCreateFailure> failures;

    /**
     * 初始化批量创建统计和失败明细。
     */
    public CardBatchCreateResult() {
        this.createdCount = 0;
        this.duplicateCount = 0;
        this.invalidCount = 0;
        this.failures = new ArrayList<>();
    }

    /**
     * 返回成功创建的卡片数量。
     */
    public Integer getCreatedCount() {
        return createdCount;
    }

    /**
     * 设置成功创建的卡片数量。
     */
    public void setCreatedCount(Integer createdCount) {
        this.createdCount = createdCount;
    }

    /**
     * 返回因为重复被跳过的卡片数量。
     */
    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    /**
     * 设置因为重复被跳过的卡片数量。
     */
    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    /**
     * 返回因为内容无效被跳过的卡片数量。
     */
    public Integer getInvalidCount() {
        return invalidCount;
    }

    /**
     * 设置因为内容无效被跳过的卡片数量。
     */
    public void setInvalidCount(Integer invalidCount) {
        this.invalidCount = invalidCount;
    }

    /**
     * 返回每张导入失败卡片的显示内容和失败原因。
     */
    public List<CardBatchCreateFailure> getFailures() {
        return failures;
    }

    /**
     * 设置每张导入失败卡片的显示内容和失败原因。
     */
    public void setFailures(List<CardBatchCreateFailure> failures) {
        this.failures = failures == null ? new ArrayList<>() : failures;
    }

    /**
     * 成功创建数量加一。
     */
    public void incrementCreatedCount() {
        this.createdCount++;
    }

    /**
     * 重复跳过数量加一。
     */
    public void incrementDuplicateCount() {
        this.duplicateCount++;
    }

    /**
     * 无效跳过数量加一。
     */
    public void incrementInvalidCount() {
        this.invalidCount++;
    }

    /**
     * 记录一张导入失败卡片和用户能看懂的原因。
     */
    public void addFailure(String sideA, String sideB, String reason) {
        this.failures.add(new CardBatchCreateFailure(sideA, sideB, reason));
    }
}
