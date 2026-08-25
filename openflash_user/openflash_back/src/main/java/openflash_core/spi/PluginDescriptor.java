package openflash_core.spi;

/**
 * 插件描述符，由具体插件实现，核心只读取插件 ID 和启用状态。
 */
public interface PluginDescriptor {

    /** 返回插件稳定 ID，供前端插件宿主匹配激活状态。 */
    String pluginId();

    /** 返回插件当前是否启用。 */
    boolean isEnabled();
}
