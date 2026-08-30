package openflash_plugin.mask_mode.mapper;

import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.type.TypeAliasRegistry;

class MaskModeMapperXmlLoadTest {

    @Test
    void maskModeDeckSettingsMapperXmlUsesPluginPackageNames() throws Exception {
        String xml = readResource("openflash_plugin/mask_mode/mapper/MaskModeDeckSettingsMapper.xml");

        assertTrue(xml.contains("openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper"));
        assertTrue(xml.contains("openflash_plugin.mask_mode.entity.MaskModeDeckSettings"));
        assertTrue(xml.contains("pw_mask_mode_deck_settings"));
        assertTrue(xml.contains("<arg column=\"enabled\" javaType=\"_boolean\"/>"));
    }

    @Test
    void maskModeEnabledConstructorArgMatchesPrimitiveBooleanRecordParameter() throws Exception {
        Class<?> enabledType = new TypeAliasRegistry().resolveAlias("_boolean");

        assertTrue(enabledType == boolean.class);
        assertNotNull(MaskModeDeckSettings.class.getDeclaredConstructor(Long.class, String.class, enabledType));
    }

    /**
     * 从测试 classpath 读取 mapper XML，验证资源路径被 Gradle 正确加载。
     */
    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
