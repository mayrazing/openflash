package openflash_plugin.tts.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * TTS 插件自动装配入口；插件存在时注册自己的组件和 mapper。
 */
@AutoConfiguration
@ComponentScan("openflash_plugin.tts")
@MapperScan(basePackages = "openflash_plugin.tts.mapper", annotationClass = Mapper.class)
public class TtsPluginAutoConfiguration {
}
