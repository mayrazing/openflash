package openflash_core.config;

import java.util.List;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import openflash_core.service.impl.PluginManifestLoader;

/** 通过插件 manifest 导入插件自动配置，core 不写具体插件类名。 */
public class PluginAutoConfigurationImportSelector implements DeferredImportSelector, ResourceLoaderAware {

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** 返回当前 classpath 中存在的插件自动配置类。 */
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> imports = PluginManifestLoader.load(resourceLoader).stream()
            .map(PluginManifestLoader.PluginManifest::autoConfigurationClass)
            .filter(className -> className != null && ClassUtils.isPresent(className, resourceLoader.getClassLoader()))
            .toList();
        return imports.toArray(String[]::new);
    }
}
