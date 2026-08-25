package openflash_core.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/** 读取插件目录中的 manifest，让新增插件只添加自己的资源文件。 */
public final class PluginManifestLoader {

    private static final String PLUGIN_MANIFEST_PATTERN = "classpath*:plugins/*/plugin.properties";

    private PluginManifestLoader() {
    }

    /** 从 classpath 扫描所有插件 manifest。 */
    public static List<PluginManifest> load(ResourceLoader resourceLoader) {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        try {
            Resource[] resources = resolver.getResources(PLUGIN_MANIFEST_PATTERN);
            List<PluginManifest> manifests = new ArrayList<>();
            for (Resource resource : resources) {
                manifests.add(read(resource));
            }
            return manifests;
        } catch (IOException ex) {
            throw new IllegalStateException("读取插件 manifest 失败", ex);
        }
    }

    private static PluginManifest read(Resource resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        }
        return new PluginManifest(
            resource.getDescription(),
            trimToNull(properties.getProperty("autoConfigurationClass")),
            trimToNull(properties.getProperty("configLocation"))
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 单个插件 manifest 里的核心声明。 */
    public record PluginManifest(String source, String autoConfigurationClass, String configLocation) {
    }
}
