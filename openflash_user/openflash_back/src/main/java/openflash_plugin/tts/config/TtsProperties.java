package openflash_plugin.tts.config;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import openflash_core.service.SystemConfigService;

/**
 * 维护本地 CosyVoice3/Piper TTS 服务配置。
 */
@Component
@ConfigurationProperties(prefix = "app.tts")
public class TtsProperties {

    private String serviceUrl;
    private String piperServiceUrl;
    private String voice;
    private double speed;
    private double piperSpeed;
    private String accent = "american";
    private String engineVersion;
    private String piperEngineVersion;
    private int maxConcurrentRequests;
    private int requestQueueCapacity = 1;
    private int maxConcurrentRequestsPerUser = 2;
    private long connectTimeoutMillis;
    private long requestTimeoutMillis;
    private long capacityWaitTimeoutMillis = 250L;
    private SystemConfigService systemConfigService;

    /**
     * 启动时兜底校验，避免把并发上限配置成无效值。
     */
    @PostConstruct
    public void validate() {
        if (maxConcurrentRequests < 1) {
            throw new IllegalArgumentException("app.tts.max-concurrent-requests 必须大于 0");
        }
        if (requestQueueCapacity < 1) {
            throw new IllegalArgumentException("app.tts.request-queue-capacity 必须大于 0");
        }
        if (maxConcurrentRequestsPerUser < 1) {
            throw new IllegalArgumentException("app.tts.max-concurrent-requests-per-user 必须大于 0");
        }
    }

    /**
     * 延迟接入系统配置服务，避免配置读取阶段形成循环依赖。
     */
    @Autowired
    @Lazy
    public void setSystemConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getPiperServiceUrl() {
        return piperServiceUrl;
    }

    public void setPiperServiceUrl(String piperServiceUrl) {
        this.piperServiceUrl = piperServiceUrl;
    }

    public String getVoice() {
        if (systemConfigService != null) {
            return systemConfigService.getString("tts.voice", voice);
        }
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public double getSpeed() {
        if (systemConfigService != null) {
            return systemConfigService.getDecimal("tts.speed", BigDecimal.valueOf(speed)).doubleValue();
        }
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getPiperSpeed() {
        if (systemConfigService != null) {
            return systemConfigService.getDecimal(
                    "tts.piper-speed", BigDecimal.valueOf(piperSpeed)).doubleValue();
        }
        return piperSpeed;
    }

    public void setPiperSpeed(double piperSpeed) {
        this.piperSpeed = piperSpeed;
    }

    public String getAccent() {
        String configured = systemConfigService != null
                ? systemConfigService.getString("tts.accent", accent)
                : accent;
        String normalized = normalizeAccent(configured);
        return normalized != null ? normalized : normalizeAccentOrDefault(accent);
    }

    public void setAccent(String accent) {
        this.accent = accent;
    }

    public String getEngineVersion() {
        if (systemConfigService != null) {
            return systemConfigService.getString("tts.engine-version", engineVersion);
        }
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getPiperEngineVersion() {
        if (systemConfigService != null) {
            return systemConfigService.getString("tts.piper-engine-version", piperEngineVersion);
        }
        return piperEngineVersion;
    }

    public void setPiperEngineVersion(String piperEngineVersion) {
        this.piperEngineVersion = piperEngineVersion;
    }

    public int getMaxConcurrentRequests() {
        if (systemConfigService != null) {
            int configuredValue = systemConfigService.getInt("tts.max-concurrent-requests", maxConcurrentRequests);
            return configuredValue > 0 ? configuredValue : maxConcurrentRequests;
        }
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getRequestQueueCapacity() {
        if (systemConfigService != null) {
            int configuredValue = systemConfigService.getInt("tts.request-queue-capacity", requestQueueCapacity);
            return configuredValue > 0 ? configuredValue : requestQueueCapacity;
        }
        return requestQueueCapacity;
    }

    public void setRequestQueueCapacity(int requestQueueCapacity) {
        this.requestQueueCapacity = requestQueueCapacity;
    }

    public int getMaxConcurrentRequestsPerUser() {
        if (systemConfigService != null) {
            int configuredValue = systemConfigService.getInt(
                    "tts.max-concurrent-requests-per-user", maxConcurrentRequestsPerUser);
            return configuredValue > 0 ? configuredValue : maxConcurrentRequestsPerUser;
        }
        return maxConcurrentRequestsPerUser;
    }

    public void setMaxConcurrentRequestsPerUser(int maxConcurrentRequestsPerUser) {
        this.maxConcurrentRequestsPerUser = maxConcurrentRequestsPerUser;
    }

    public long getConnectTimeoutMillis() {
        if (systemConfigService != null) {
            return systemConfigService.getLong("tts.connect-timeout-millis", connectTimeoutMillis);
        }
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getRequestTimeoutMillis() {
        if (systemConfigService != null) {
            return systemConfigService.getLong("tts.request-timeout-millis", requestTimeoutMillis);
        }
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public long getCapacityWaitTimeoutMillis() {
        if (systemConfigService != null) {
            long configuredValue = systemConfigService.getLong(
                "tts.capacity-wait-timeout-millis", capacityWaitTimeoutMillis);
            return configuredValue > 0 ? configuredValue : capacityWaitTimeoutMillis;
        }
        return capacityWaitTimeoutMillis;
    }

    public void setCapacityWaitTimeoutMillis(long capacityWaitTimeoutMillis) {
        this.capacityWaitTimeoutMillis = capacityWaitTimeoutMillis;
    }

    private static String normalizeAccent(String value) {
        if (value != null && "american".equalsIgnoreCase(value.trim())) {
            return "american";
        }
        return null;
    }

    private static String normalizeAccentOrDefault(String value) {
        String normalized = normalizeAccent(value);
        return normalized != null ? normalized : "american";
    }
}
