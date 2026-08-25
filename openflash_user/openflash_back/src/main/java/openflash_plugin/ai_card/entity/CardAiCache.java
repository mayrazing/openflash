package openflash_plugin.ai_card.entity;

import java.time.LocalDateTime;

/**
 * 保存某段卡片内容的 AI 结果缓存。
 */
public class CardAiCache {

    private Long id;
    private Long ownerUserId;
    private String contentFingerprint;
    private String promptFingerprint;
    private String prompt;
    private String content;
    private Boolean thinkUsed;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime lastGeneratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public String getPromptFingerprint() {
        return promptFingerprint;
    }

    public void setPromptFingerprint(String promptFingerprint) {
        this.promptFingerprint = promptFingerprint;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getThinkUsed() {
        return thinkUsed;
    }

    public void setThinkUsed(Boolean thinkUsed) {
        this.thinkUsed = thinkUsed;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public LocalDateTime getLastGeneratedAt() {
        return lastGeneratedAt;
    }

    public void setLastGeneratedAt(LocalDateTime lastGeneratedAt) {
        this.lastGeneratedAt = lastGeneratedAt;
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
}
