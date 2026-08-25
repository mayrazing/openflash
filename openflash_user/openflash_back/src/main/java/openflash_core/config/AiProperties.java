package openflash_core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import openflash_core.service.SystemConfigService;
import openflash_core.service.TypeRegistryService;

/**
 * 通用 AI provider/profile 配置读取器。
 * 该类属于模型调用基础设施，不绑定具体功能插件。
 */
@Component
@ConfigurationProperties(prefix = "app.ai-runtime")
public class AiProperties {

    private long timeoutMillis;
    private List<AiProfile> profiles = new ArrayList<>();
    private Map<String, String> featureProfiles = Map.of();
    private SystemConfigService systemConfigService;
    private TypeRegistryService typeRegistryService;

    /**
     * 延迟注入系统配置服务，避免启动配置绑定时产生循环依赖。
     */
    @Autowired
    @Lazy
    public void setSystemConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 延迟注入类型注册表服务，优先读取数据库中的 AI profile。
     */
    @Autowired
    @Lazy
    public void setTypeRegistryService(TypeRegistryService typeRegistryService) {
        this.typeRegistryService = typeRegistryService;
    }

    public long getTimeoutMillis() {
        if (systemConfigService != null) {
            return systemConfigService.getLong("ai.timeout-millis", timeoutMillis);
        }
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public List<AiProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<AiProfile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : profiles;
    }

    public Map<String, String> getFeatureProfiles() {
        return featureProfiles;
    }

    public void setFeatureProfiles(Map<String, String> featureProfiles) {
        this.featureProfiles = featureProfiles == null ? Map.of() : featureProfiles;
    }

    /**
     * 按功能 key 解析对应 profile。模型行为字段始终以 profile 为准。
     */
    public AiProfile resolveProfile(String featureKey) {
        if (typeRegistryService != null) {
            AiProfile dbProfile = typeRegistryService.resolveAiProfile(featureKey);
            if (dbProfile != null) {
                return dbProfile;
            }
        }

        String profileName = featureProfiles.get(featureKey);
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalStateException("未配置 AI 功能 " + featureKey + " 对应的 profile");
        }
        for (AiProfile profile : profiles) {
            if (profileName.equals(profile.getName())) {
                return profile;
            }
        }
        throw new IllegalStateException("AI profile 不存在: " + profileName);
    }

    /**
     * AI 功能的命名 profile。
     */
    public static class AiProfile {
        private String name;
        private String model;
        private String system;
        private double temperature;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
