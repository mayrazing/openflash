package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.PracticeModeOption;

/**
 * 类型注册表数据访问接口，读取启用项的配置和键名列表。
 */
@Mapper
public interface TypeRegistryMapper {

    /**
     * 查询指定类型和键名的启用配置 JSON。
     *
     * @param registryType 注册类型
     * @param itemKey 项键名
     * @return 配置 JSON
     */
    String findConfigJson(@Param("registryType") String registryType, @Param("itemKey") String itemKey);

    /**
     * 查询指定类型下所有启用项的键名列表。
     *
     * @param registryType 注册类型
     * @return 启用项键名列表
     */
    List<String> findEnabledItemKeys(@Param("registryType") String registryType);

    /**
     * 查询已启用的练习模式选项。
     *
     * @param registryType 注册类型
     * @return 页面可展示的练习模式选项
     */
    List<PracticeModeOption> findEnabledPracticeModes(@Param("registryType") String registryType);

    /**
     * 查询某注册类型下所有启用项的目录条目（键名/显示名/配置 JSON）。
     *
     * @param registryType 注册类型
     * @return 目录条目列表
     */
    List<openflash_core.entity.PluginCatalogItem> findCatalog(@Param("registryType") String registryType);
}
