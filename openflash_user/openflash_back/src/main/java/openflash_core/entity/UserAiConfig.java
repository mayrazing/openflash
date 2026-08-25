package openflash_core.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;

/** 用户某个 personal AI provider 的连接配置; active 由统一 selection join 计算. */
public class UserAiConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Long id;
    private Long userId;
    private String provider;
    private String configJson;
    private boolean active;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 从 configJson 读取指定 key 的值，JSON 格式错误或 key 不存在时返回 null。 */
    public String getConfigValue(String key) {
        if (configJson == null || configJson.isBlank()) return null;
        try {
            Map<String, String> map = JSON.readValue(configJson, new TypeReference<>() {});
            return map.get(key);
        } catch (Exception e) {
            return null;
        }
    }
}
