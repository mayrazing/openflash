package openflash_ai_runtime.integration;

import openflash_ai_runtime.AiRuntimeApplication;
import openflash_ai_runtime.mapper.RuntimeSystemConfigMapper;
import openflash_ai_runtime.client.CodexAppServerClient;
import openflash_ai_runtime.config.CodexHome;
import openflash_ai_runtime.controller.AiRuntimeCliAdminController;
import openflash_ai_runtime.controller.PlatformAiCoreController;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.support.CodexLoginCoordinator;
import openflash_ai_runtime.support.CodexProcessManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = AiRuntimeApplication.class,
    properties = {
        "app.internal.admin-token=boot-admin-token",
        "app.internal.core-token=boot-core-token",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
    })
@Import(CodexRuntimeEnabledStartupTest.RuntimeConfig.class)
class CodexRuntimeEnabledStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void enabledRuntimeBuildsCompleteLazyCodexBeanGraphWithoutLaunchingProcess() {
        CodexProcessManager processManager = context.getBean(CodexProcessManager.class);

        assertThat(context.getBean(CodexHome.class)).isNotNull();
        assertThat(context.getBean(CodexModelCatalog.class)).isNotNull();
        assertThat(context.getBean(CodexAppServerClient.class)).isNotNull();
        assertThat(context.getBean(CodexLoginCoordinator.class)).isNotNull();
        assertThat(context.getBean(CodexRuntimeService.class)).isNotNull();
        assertThat(context.getBean(AiRuntimeCliAdminController.class)).isNotNull();
        assertThat(context.getBean(PlatformAiCoreController.class)).isNotNull();
        assertThat(ReflectionTestUtils.getField(processManager, "current")).isNull();
        assertThat(ReflectionTestUtils.getField(processManager, "starting")).isNull();
    }

    @TestConfiguration
    static class RuntimeConfig {

        @Bean
        RuntimeSystemConfigMapper runtimeSystemConfigMapper() {
            return key -> null;
        }
    }
}
