package openflash_ai_runtime;

import openflash_ai_runtime.config.AiRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiRuntimeProperties.class)
public class AiRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiRuntimeApplication.class, args);
    }
}
