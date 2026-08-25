package openflash_plugin.ai_card.config;

import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;

import org.springframework.stereotype.Component;
import openflash_core.spi.PluginDescriptor;

/** AI Card 插件描述，通过 guard 读取插件 active 开关。 */
@Component
public class AiCardPluginDescriptor implements PluginDescriptor {

    /** AI 卡片插件 id，供门控等处复用，避免硬编码字面量。 */
    public static final String PLUGIN_ID = "ai-card";

    private final AiCardFeatureGuard featureGuard;

    public AiCardPluginDescriptor(AiCardFeatureGuard featureGuard) {
        this.featureGuard = featureGuard;
    }

    @Override
    public String pluginId() {
        return PLUGIN_ID;
    }

    @Override
    public boolean isEnabled() {
        return featureGuard.isAiCardEnabled();
    }
}
