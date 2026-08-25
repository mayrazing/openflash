package openflash_plugin.mask_mode.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MaskModeMigrationContentTest {

    @Test
    void pluginCatalogRegistrationUsesLanguageNeutralFallbacks() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V45__register_mask_mode_plugin.sql"));

        assertTrue(sql.contains("'plugins.mask-mode.name'"));
        assertTrue(sql.contains("\"descKey\":\"plugins.mask-mode.desc\""));
        assertTrue(sql.contains("\"categoryKey\":\"pluginCategories.studyAid\""));
        assertFalse(sql.contains("'遮蔽模式'"));
        assertFalse(sql.contains("\"desc\":\"按随机或完全规则整面遮蔽题目，支持按住显示\""));
    }
}
