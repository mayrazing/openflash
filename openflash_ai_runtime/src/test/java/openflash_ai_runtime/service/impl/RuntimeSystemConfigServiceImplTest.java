package openflash_ai_runtime.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import openflash_ai_runtime.service.RuntimeSystemConfigService;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeSystemConfigServiceImplTest {

    @Test
    void readsDatabaseValuesAndFallsBackForMissingOrMalformedLongs() {
        Map<String, String> values = Map.of(
            "present", "configured",
            "valid-long", "42",
            "invalid-long", "not-a-number");
        RuntimeSystemConfigService service = new RuntimeSystemConfigServiceImpl(
            (Function<String, String>) values::get);

        assertThat(service.getString("present", "fallback")).isEqualTo("configured");
        assertThat(service.getString("missing", "fallback")).isEqualTo("fallback");
        assertThat(service.getLong("valid-long", 7L)).isEqualTo(42L);
        assertThat(service.getLong("missing-long", 7L)).isEqualTo(7L);
        assertThat(service.getLong("invalid-long", 7L)).isEqualTo(7L);
    }

    @Test
    void codexCatalogTimeoutUsesDatabaseValueOrFiveSecondFallback() {
        Map<String, String> configured = Map.of(
            "configured", "1250",
            "invalid", "not-a-number");
        RuntimeSystemConfigService service = new RuntimeSystemConfigServiceImpl(
            (Function<String, String>) configured::get);

        assertThat(service.getLong("configured", 5000L)).isEqualTo(1250L);
        assertThat(service.getLong("missing", 5000L)).isEqualTo(5000L);
        assertThat(service.getLong("invalid", 5000L)).isEqualTo(5000L);
    }
}
