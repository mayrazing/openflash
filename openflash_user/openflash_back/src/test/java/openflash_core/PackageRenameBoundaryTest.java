package openflash_core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PackageRenameBoundaryTest {

    private static final String OLD_ROOT = "pick_word" + "_back";
    private static final String OLD_CORE_PLUGIN = OLD_ROOT + ".core.plugin";
    private static final String OLD_CONCRETE_PLUGIN = OLD_ROOT + ".plugin";
    private static final String OLD_APPLICATION = "PickWordBack" + "Application";
    private static final String PLUGIN_ROOT = "openflash_" + "plugin";

    @Test
    void javaSourcesDoNotUseOldPickWordBackPackages() throws IOException {
        Path root = Path.of("src/main/java");

        try (Stream<Path> files = Files.walk(root)) {
            boolean hasOldPackage = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PackageRenameBoundaryTest::read)
                    .anyMatch(source -> source.contains("package " + OLD_ROOT)
                            || source.contains("import " + OLD_ROOT)
                            || source.contains(OLD_CORE_PLUGIN)
                            || source.contains(OLD_CONCRETE_PLUGIN)
                            || source.contains(OLD_APPLICATION));

            assertFalse(hasOldPackage, "Found old pick_word_back package/import in Java sources");
        }
    }

    @Test
    void resourcesDoNotUseOldJavaTypeNames() throws IOException {
        Path resources = Path.of("src/main/resources");

        try (Stream<Path> files = Files.walk(resources)) {
            boolean hasOldTypeName = files
                    .filter(Files::isRegularFile)
                    .map(PackageRenameBoundaryTest::read)
                    .anyMatch(source -> source.contains(OLD_ROOT + ".entity")
                            || source.contains(OLD_ROOT + ".mapper")
                            || source.contains(OLD_CORE_PLUGIN)
                            || source.contains(OLD_CONCRETE_PLUGIN)
                            || source.contains(OLD_APPLICATION));

            assertFalse(hasOldTypeName, "Found old pick_word_back type name in resources");
        }
    }

    @Test
    void coreDoesNotReferencePluginPackages() throws IOException {
        Path coreRoot = Path.of("src/main/java/openflash_core");

        if (!Files.exists(coreRoot)) {
            // 目录尚未创建（迁移前阶段），无任何 import，断言直接通过
            return;
        }

        try (Stream<Path> files = Files.walk(coreRoot)) {
            boolean referencesPluginPackage = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PackageRenameBoundaryTest::read)
                    .anyMatch(source -> source.contains(PLUGIN_ROOT));

            assertFalse(referencesPluginPackage, "openflash_core references plugin package namespace");
        }
    }

    @Test
    void coreProductionConfigDoesNotNamePluginPackages() throws IOException {
        String application = Files.readString(Path.of("src/main/java/openflash_core/OpenFlashApplication.java"));
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertFalse(application.contains(PLUGIN_ROOT), "OpenFlashApplication names a plugin package");
        assertFalse(yaml.contains(PLUGIN_ROOT), "application.yaml names a plugin package");
        assertFalse(yaml.contains("application-ai.yaml"));
        assertFalse(yaml.contains("application-tts.yaml"));
    }

    @Test
    void coreTestsDoNotReferencePluginPackages() throws IOException {
        Path coreTests = Path.of("src/test/java/openflash_core");

        try (Stream<Path> files = Files.walk(coreTests)) {
            boolean referencesPluginPackage = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("PackageRenameBoundaryTest.java"))
                    .map(PackageRenameBoundaryTest::read)
                    .anyMatch(source -> source.contains(PLUGIN_ROOT));

            assertFalse(referencesPluginPackage, "openflash_core tests reference plugin package namespace");
        }
    }

    @Test
    void applicationScansOnlyCorePackage() throws IOException {
        String source = Files.readString(Path.of("src/main/java/openflash_core/OpenFlashApplication.java"));

        assertTrue(source.contains("\"openflash_core\""));
        assertFalse(source.contains("\"" + PLUGIN_ROOT + "\""));
        assertFalse(source.contains("\"" + OLD_ROOT + "\""), "Found old pick_word_back package scan in application");
        assertFalse(source.contains(OLD_APPLICATION), "Found old application class name reference");
    }

    // 验证核心配置只声明通用 mapper XML 入口，不写具体插件包路径。
    @Test
    void coreMybatisConfigDoesNotNamePluginMapperXml() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertTrue(yaml.contains("classpath:mapper/*.xml"));
        assertFalse(yaml.contains(PLUGIN_ROOT));
    }

    // 验证核心 mapper 不写具体插件任务类型；插件任务策略由插件 spec 提供。
    @Test
    void coreAsyncTaskMapperDoesNotNamePluginTaskTypes() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/AsyncTaskMapper.xml"));

        assertFalse(xml.contains("TTS_PREWARM"));
        assertFalse(xml.contains("AI_CACHE_BUILD"));
        assertTrue(xml.contains("rescheduleFailed"));
    }

    @Test
    void coreRuntimeResourcesDoNotNameConcretePluginPackages() throws IOException {
        Path resources = Path.of("src/main/resources");
        Set<Path> coreRuntimeResources = Set.of(
                resources.resolve("application.yaml"),
                resources.resolve("META-INF/spring.factories"),
                resources.resolve("META-INF/spring/org.springframework.context.ApplicationContextInitializer"),
                resources.resolve("mapper/AsyncTaskMapper.xml"));

        for (Path path : coreRuntimeResources) {
            String source = Files.readString(path);
            assertFalse(source.contains(PLUGIN_ROOT), "Core runtime resource names plugin package: " + path);
            assertFalse(source.contains("TTS_PREWARM"), "Core runtime resource names TTS task type: " + path);
            assertFalse(source.contains("AI_CACHE_BUILD"), "Core runtime resource names AI task type: " + path);
        }
    }

    @Test
    void pluginDiscoveryUsesGenericManifestPath() throws IOException {
        String loader = Files.readString(Path.of("src/main/java/openflash_core/service/impl/PluginManifestLoader.java"));

        assertTrue(loader.contains("plugins/*/plugin.properties"));
        assertFalse(loader.contains(PLUGIN_ROOT));
    }

    // 验证 core 包不继续承载卡片 AI/TTS 插件业务类型；通用 AI 配置与运行时归 core 所有。
    @Test
    void coreDoesNotOwnAiOrTtsPluginBusinessTypes() throws IOException {
        Set<String> forbidden = Set.of(
                "src/main/java/openflash_core/controller/DeckAiSettingsController.java",
                "src/main/java/openflash_core/mapper/DeckAiSettingsMapper.java",
                "src/main/java/openflash_core/mapper/CardAiCacheMapper.java",
                "src/main/java/openflash_core/mapper/TtsCacheMetaMapper.java",
                "src/main/java/openflash_core/entity/DeckAiSettings.java",
                "src/main/java/openflash_core/entity/CardAiCache.java",
                "src/main/java/openflash_core/entity/TtsCacheMeta.java",
                "src/main/java/openflash_core/entity/CardAiRebuildTarget.java",
                "src/main/java/openflash_core/entity/AiCacheStatusResponse.java",
                "src/main/java/openflash_core/entity/DeckAiSettingsUpdateCommand.java",
                "src/main/java/openflash_core/entity/AiCacheReadyNotification.java");

        for (String path : forbidden) {
            assertFalse(Files.exists(Path.of(path)), "Plugin business type remains in core: " + path);
        }
    }

    @Test
    void aiCollocationsPluginPackageIsRemoved() throws IOException {
        Path packageRoot = Path.of("src/main/java/" + PLUGIN_ROOT + "/ai_collocations");
        assertFalse(Files.exists(packageRoot), "ai_collocations plugin package should be removed");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源文件失败: " + path, ex);
        }
    }

}
