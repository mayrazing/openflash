package openflash_plugin.tts.config;

import openflash_plugin.tts.service.impl.TtsFeatureGuard;

import org.springframework.stereotype.Component;
import openflash_core.spi.PluginDescriptor;

/** TTS 插件描述, 读取统一 feature.tts 开关判断是否激活. */
@Component
public class TtsPluginDescriptor implements PluginDescriptor {

    private final TtsFeatureGuard featureGuard;

    public TtsPluginDescriptor(TtsFeatureGuard featureGuard) {
        this.featureGuard = featureGuard;
    }

    @Override
    public String pluginId() {
        return "tts";
    }

    @Override
    public boolean isEnabled() {
        return featureGuard.isTtsEnabled();
    }
}
