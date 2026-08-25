package openflash_core.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AsyncTaskLatencyDefaultsTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yaml");
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V57__reduce_async_task_poll_delay.sql");

    @Test
    void defaultsAsyncTaskPollingToOneSecondInYamlAndDatabase() throws Exception {
        String yaml = Files.readString(APPLICATION_YAML);

        assertTrue(yaml.contains("fixed-delay-millis: 1000"));
        assertTrue(Files.exists(MIGRATION));

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("UPDATE `pw_system_config`"));
        assertTrue(sql.contains("SET `value` = '1000'"));
        assertTrue(sql.contains("WHERE `config_key` = 'async-task.fixed-delay-millis'"));
    }
}
