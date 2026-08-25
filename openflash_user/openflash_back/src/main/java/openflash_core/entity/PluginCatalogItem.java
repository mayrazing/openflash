package openflash_core.entity;

/** 插件目录条目，供市场「全部」列表展示。config 为原始 JSON 字符串（desc/icon/category）。 */
public class PluginCatalogItem {
    private String pluginId;
    private String name;
    private String config;

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
}
