package openflash_ai_runtime.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RuntimeSystemConfigMapperXmlTest {

    @Test
    void systemConfigQueryUsesPostgresqlCompatibleIdentifiers() throws IOException {
        String xml = new ClassPathResource("mapper/RuntimeSystemConfigMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml).doesNotContain("`");
        assertThat(xml).contains(
                "SELECT value",
                "FROM pw_system_config",
                "WHERE config_key = #{key}");
    }
}
