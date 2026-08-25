package openflash_plugin.mask_mode.config;

import openflash_plugin.mask_mode.service.impl.MaskModeFeatureGuard;

import org.springframework.stereotype.Component;
import openflash_core.spi.PluginDescriptor;

/** 遮蔽模式插件描述，读取 feature.mask-mode 开关判断是否激活。 */
@Component
public class MaskModePluginDescriptor implements PluginDescriptor {

    private final MaskModeFeatureGuard featureGuard;

    /** 注入遮蔽模式功能 guard，避免描述器直接关心开关键名。 */
    public MaskModePluginDescriptor(MaskModeFeatureGuard featureGuard) {
        this.featureGuard = featureGuard;
    }

    @Override
    public String pluginId() {
        return "mask-mode";
    }

    @Override
    public boolean isEnabled() {
        return featureGuard.isMaskModeEnabled();
    }
}
