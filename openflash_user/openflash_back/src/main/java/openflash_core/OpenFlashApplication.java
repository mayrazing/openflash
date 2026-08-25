package openflash_core;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import openflash_core.config.PluginAutoConfigurationImportSelector;

@SpringBootApplication(
    scanBasePackages = {
        "openflash_core"
    }
)
@EnableAsync
@EnableScheduling
@Import(PluginAutoConfigurationImportSelector.class)
@MapperScan(
    basePackages = {
        "openflash_core"
    },
    annotationClass = Mapper.class
)
public class OpenFlashApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(OpenFlashApplication.class, args);
    }
}
