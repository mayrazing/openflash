package openflash_core.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import openflash_core.spi.PluginDescriptor;

/** 收集所有 PluginDescriptor Bean，向核心和前端暴露启用插件列表。 */
@Service
public class PluginRegistry {

    private final List<PluginDescriptor> plugins;

    public PluginRegistry(List<PluginDescriptor> plugins) {
        this.plugins = plugins;
    }

    /** 返回当前所有启用插件的 ID 列表。 */
    public List<String> activePluginIds() {
        return plugins.stream()
            .filter(PluginDescriptor::isEnabled)
            .map(PluginDescriptor::pluginId)
            .toList();
    }
}
