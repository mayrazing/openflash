package openflash_ai_runtime.integration;

import openflash_ai_runtime.AiRuntimeApplication;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformSecretService;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.RuntimeSystemConfigMapper;
import openflash_ai_runtime.controller.PlatformAiAdminController;
import openflash_ai_runtime.controller.PlatformAiCoreController;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.support.CodexProcessManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = AiRuntimeApplication.class,
    properties = {
        "app.codex.enabled=false",
        "app.internal.admin-token=boot-admin-token",
        "app.internal.core-token=boot-core-token",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable"
            + "?connectTimeout=1&socketTimeout=1",
        "spring.datasource.username=unavailable",
        "spring.datasource.password=unavailable",
        "spring.datasource.hikari.initialization-fail-timeout=100"
    })
class CodexRuntimeDisabledStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void disabledRuntimeStartsWithoutAnyCodexProcessOwnerBean() {
        assertThat(context.getBean(DataSource.class)).isNotNull();
        assertThat(context.getBean(RuntimeSystemConfigMapper.class)).isNotNull();
        assertThat(context.getBean(PlatformAiConnectionMapper.class)).isNotNull();
        assertThat(context.getBean(PlatformSecretService.class)).isNotNull();
        assertThat(context.getBean(PlatformAiCatalogService.class)).isNotNull();
        assertThat(context.getBean(PlatformAiAdminController.class)).isNotNull();
        assertThat(context.getBean(PlatformAiCoreController.class)).isNotNull();
        assertThat(context.getBeansOfType(CodexProcessManager.class)).isEmpty();
        assertThat(context.getBeansOfType(CodexRuntimeService.class)).isEmpty();
    }
}
