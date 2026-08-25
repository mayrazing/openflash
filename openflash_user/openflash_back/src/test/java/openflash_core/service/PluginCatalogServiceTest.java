package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import openflash_core.entity.PluginCatalogItem;
import openflash_core.service.impl.PluginRegistry;
import openflash_core.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class PluginCatalogServiceTest {

    record StubPlugin(String id, boolean enabled) implements PluginDescriptor {
        @Override public String pluginId() { return id; }
        @Override public boolean isEnabled() { return enabled; }
    }

    private static PluginCatalogItem item(String id) {
        PluginCatalogItem i = new PluginCatalogItem();
        i.setPluginId(id); i.setName(id); i.setConfig("{}");
        return i;
    }

    @Test
    void catalogKeepsOnlyGloballyEnabledPlugins() {
        List<PluginCatalogItem> rows = List.of(item("tts"), item("ai-card"));
        PluginRegistry registry = new PluginRegistry(List.of(
            new StubPlugin("tts", true), new StubPlugin("ai-card", false)));
        PluginCatalogService svc = new PluginCatalogService(type -> rows, registry);

        List<PluginCatalogItem> result = svc.catalog();

        assertEquals(1, result.size());
        assertEquals("tts", result.get(0).getPluginId());
    }
}
