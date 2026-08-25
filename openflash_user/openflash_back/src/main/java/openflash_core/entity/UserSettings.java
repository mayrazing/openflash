package openflash_core.entity;

import java.time.LocalDateTime;

public class UserSettings {

    private Long id;
    private Long userId;
    private String theme;
    private Boolean soundEnabled;
    private LocalDateTime lastExportedAt;
    private LocalDateTime updatedAt;
    private String language;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public Boolean getSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(Boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public LocalDateTime getLastExportedAt() { return lastExportedAt; }
    public void setLastExportedAt(LocalDateTime lastExportedAt) { this.lastExportedAt = lastExportedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
