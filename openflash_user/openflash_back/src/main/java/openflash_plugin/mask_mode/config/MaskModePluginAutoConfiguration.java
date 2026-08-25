package openflash_plugin.mask_mode.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 遮蔽模式插件自动装配入口；插件存在时注册自己的组件和 mapper。
 */
@AutoConfiguration
@ComponentScan("openflash_plugin.mask_mode")
@MapperScan(basePackages = "openflash_plugin.mask_mode.mapper", annotationClass = Mapper.class)
public class MaskModePluginAutoConfiguration {
}
