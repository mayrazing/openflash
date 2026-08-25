package openflash_core.service;

import openflash_core.controller.UserAiConfigController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import openflash_core.config.AiProperties;
import org.junit.jupiter.api.Test;

class AiGatewayContractTest {

    @Test
    void aiGatewayAcceptsExplicitProfileSnapshot() throws Exception {
        Method chat = AiGateway.class.getMethod(
                "chat", String.class, AiProperties.AiProfile.class, Long.class);

        assertEquals(String.class, chat.getReturnType());
    }

    @Test
    void providerSettingsControllerHasNoPluginDependency() {
        assertTrue(Arrays.stream(UserAiConfigController.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .noneMatch(type -> type.getName().contains("openflash_" + "plugin")));
    }

    @Test
    void profileResolverPublishesEveryOperationUsedByPlugins() throws Exception {
        assertTrue(AiProfileResolver.class.isInterface());
        assertEquals(AiProperties.AiProfile.class, AiProfileResolver.class
                .getMethod("applyModel", AiProperties.AiProfile.class, String.class).getReturnType());
        assertEquals(String.class, AiProfileResolver.class
                .getMethod("readUserModel", Long.class).getReturnType());
        assertEquals(String.class, AiProfileResolver.class
                .getMethod("resolveUserModelOrNull", Long.class).getReturnType());
        assertEquals(AiProperties.AiProfile.class, AiProfileResolver.class
                .getMethod("applyUserModel", AiProperties.AiProfile.class, Long.class).getReturnType());
    }
}
