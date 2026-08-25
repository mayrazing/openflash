package openflash_core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class DatabaseEnvironmentOverrideTest {

    @Test
    void applicationYamlUsesDocumentedDatabaseUrlOverride() throws Exception {
        String expected = "jdbc:postgresql://127.0.0.1:5432/openflash_sdd_contract"
                + "?currentSchema=openflash";
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource(
                "acceptanceEnvironment",
                Map.of(
                        "OPENFLASH_DB_URL", expected,
                        "OPENFLASH_DB_USERNAME", "sdd-user",
                        "OPENFLASH_DB_PASSWORD", "sdd-password")));
        sources.addLast(new YamlPropertySourceLoader()
                .load("applicationYaml", new ClassPathResource("application.yaml"))
                .get(0));

        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertEquals(expected, resolver.getProperty("spring.datasource.url"));
        assertEquals("sdd-user", resolver.getProperty("spring.datasource.username"));
        assertEquals("sdd-password", resolver.getProperty("spring.datasource.password"));
    }
}
