package openflash_core.config;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import openflash_core.service.impl.PluginManifestLoader;

/**
 * 加载插件 manifest 声明的 YAML 默认配置，避免 core application.yaml 点名插件配置。
 * Spring Boot 4.0 起 EnvironmentPostProcessor 已弃用，改用
 * ApplicationContextInitializer。
 */
public class PluginEnvironmentPostProcessor
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    /** 将插件配置作为低优先级默认值加入环境。 */
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        for (PluginManifestLoader.PluginManifest manifest : PluginManifestLoader.load(resourceLoader)) {
            String configLocation = manifest.configLocation();
            if (configLocation == null) {
                continue;
            }
            addYamlPropertySources(environment, manifest, resourceLoader.getResource(configLocation));
        }
    }

    private void addYamlPropertySources(
            ConfigurableEnvironment environment,
            PluginManifestLoader.PluginManifest manifest,
            Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException("插件配置不存在: " + manifest.configLocation() + " from " + manifest.source());
        }
        try {
            List<PropertySource<?>> propertySources = yamlLoader.load("pluginConfig:" + manifest.source(), resource);
            for (PropertySource<?> propertySource : propertySources) {
                environment.getPropertySources().addLast(propertySource);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("加载插件配置失败: " + manifest.configLocation(), ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
