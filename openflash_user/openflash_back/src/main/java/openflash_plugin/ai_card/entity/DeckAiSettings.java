package openflash_plugin.ai_card.entity;

import java.time.LocalDateTime;

/** 存储卡包级 AI 提示词开关和提示词文本。 */
public class DeckAiSettings {

    private Long id;
    private Long deckId;
    private Boolean aiExplanationEnabledA;
    private Boolean aiExplanationEnabledB;
    private String aiExplanationPromptA;
    private String aiExplanationPromptB;
    private Boolean aiCompletionEnabled;
    private String aiCompletionPrompt;
    private LocalDateTime updatedAt;

    /** 返回设置记录的主键 ID。 */
    public Long getId() {
        return id;
    }

    /** 设置设置记录的主键 ID。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 返回这些 AI 设置所属的卡包 ID。 */
    public Long getDeckId() {
        return deckId;
    }

    /** 设置这些 AI 设置所属的卡包 ID。 */
    public void setDeckId(Long deckId) {
        this.deckId = deckId;
    }

    /** 返回 A 面解析功能是否启用。 */
    public Boolean getAiExplanationEnabledA() {
        return aiExplanationEnabledA;
    }

    /** 设置 A 面解析功能是否启用。 */
    public void setAiExplanationEnabledA(Boolean aiExplanationEnabledA) {
        this.aiExplanationEnabledA = aiExplanationEnabledA;
    }

    /** 返回 B 面解析功能是否启用。 */
    public Boolean getAiExplanationEnabledB() {
        return aiExplanationEnabledB;
    }

    /** 设置 B 面解析功能是否启用。 */
    public void setAiExplanationEnabledB(Boolean aiExplanationEnabledB) {
        this.aiExplanationEnabledB = aiExplanationEnabledB;
    }

    /** 返回卡片解析 A 面提示词文本。 */
    public String getAiExplanationPromptA() {
        return aiExplanationPromptA;
    }

    /** 设置卡片解析 A 面提示词文本。 */
    public void setAiExplanationPromptA(String aiExplanationPromptA) {
        this.aiExplanationPromptA = aiExplanationPromptA;
    }

    /** 返回卡片解析 B 面提示词文本。 */
    public String getAiExplanationPromptB() {
        return aiExplanationPromptB;
    }

    /** 设置卡片解析 B 面提示词文本。 */
    public void setAiExplanationPromptB(String aiExplanationPromptB) {
        this.aiExplanationPromptB = aiExplanationPromptB;
    }

    /** 返回补全另一面功能是否启用。 */
    public Boolean getAiCompletionEnabled() {
        return aiCompletionEnabled;
    }

    /** 设置补全另一面功能是否启用。 */
    public void setAiCompletionEnabled(Boolean aiCompletionEnabled) {
        this.aiCompletionEnabled = aiCompletionEnabled;
    }

    /** 返回补全另一面提示词文本。 */
    public String getAiCompletionPrompt() {
        return aiCompletionPrompt;
    }

    /** 设置补全另一面提示词文本。 */
    public void setAiCompletionPrompt(String aiCompletionPrompt) {
        this.aiCompletionPrompt = aiCompletionPrompt;
    }

    /** 返回这条设置最后一次更新的时间。 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 设置这条设置最后一次更新的时间。 */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
