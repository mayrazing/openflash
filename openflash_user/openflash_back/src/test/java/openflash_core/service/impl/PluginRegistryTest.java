package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import openflash_core.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

    @Test
    void returnsOnlyEnabledPluginIds() {
        PluginDescriptor enabled = new StubPlugin("tts", true);
        PluginDescriptor disabled = new StubPlugin("ai-card", false);
        PluginRegistry registry = new PluginRegistry(List.of(enabled, disabled));

        assertEquals(List.of("tts"), registry.activePluginIds());
    }

    @Test
    void returnsEmptyWhenNoPluginsEnabled() {
        PluginRegistry registry = new PluginRegistry(List.of(new StubPlugin("tts", false)));

        assertTrue(registry.activePluginIds().isEmpty());
    }

    @Test
    void returnsEmptyWhenNoPluginsRegistered() {
        PluginRegistry registry = new PluginRegistry(List.of());

        assertTrue(registry.activePluginIds().isEmpty());
    }

    private record StubPlugin(String id, boolean enabled) implements PluginDescriptor {
        @Override public String pluginId() { return id; }
        @Override public boolean isEnabled() { return enabled; }
    }
}
