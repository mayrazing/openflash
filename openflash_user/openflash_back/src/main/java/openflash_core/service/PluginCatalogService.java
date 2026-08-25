package openflash_core.service;

import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.entity.PluginCatalogItem;
import openflash_core.mapper.TypeRegistryMapper;
import openflash_core.service.impl.PluginRegistry;

/** 读取插件目录（pw_type_registry 的 registry_type='plugin'），并过滤掉全局关闭的插件。 */
@Service
public class PluginCatalogService {

    private static final String REGISTRY_TYPE_PLUGIN = "plugin";

    private final Function<String, List<PluginCatalogItem>> catalogLoader;
    private final PluginRegistry registry;

    @Autowired
    public PluginCatalogService(TypeRegistryMapper mapper, PluginRegistry registry) {
        this(mapper::findCatalog, registry);
    }

    /** 供包内测试替换数据库读取。 */
    PluginCatalogService(Function<String, List<PluginCatalogItem>> catalogLoader, PluginRegistry registry) {
        this.catalogLoader = catalogLoader;
        this.registry = registry;
    }

    /** 返回全局启用的插件目录条目。 */
    public List<PluginCatalogItem> catalog() {
        List<String> active = registry.activePluginIds();
        List<PluginCatalogItem> rows = catalogLoader.apply(REGISTRY_TYPE_PLUGIN);
        return rows == null ? List.of()
            : rows.stream().filter(it -> active.contains(it.getPluginId())).toList();
    }
}
