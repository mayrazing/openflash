package openflash_core.controller;

import openflash_core.service.impl.PluginRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import openflash_core.dto.ApiResponse;
import openflash_core.mapper.FeatureFlagMapper;
import openflash_core.service.FeatureFlagService;
import openflash_core.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class PluginControllerTest {

    private static final FeatureFlagMapper enabledMapper = new FeatureFlagMapper() {
        @Override public Boolean findGlobalEnabled(String featureKey) { return true; }
    };

    @Test
    void returnsActivePluginIdsFromRegistry() {
        PluginRegistry registry = new PluginRegistry(List.of(
            new StubPlugin("tts", true),
            new StubPlugin("ai-card", false)
        ));
        PluginController controller = new PluginController(registry, null, null, null, new FeatureFlagService(enabledMapper));

        ApiResponse<List<String>> response = controller.getActivePlugins();

        assertEquals(List.of("tts"), response.getData());
    }

    private record StubPlugin(String id, boolean enabled) implements PluginDescriptor {
        @Override public String pluginId() { return id; }
        @Override public boolean isEnabled() { return enabled; }
    }
}
