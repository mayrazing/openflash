package openflash_core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 定义 core 调用 ai_runtime core-scope API 的地址、token 与超时. */
@Component
@ConfigurationProperties(prefix = "app.ai-runtime")
public class AiRuntimeCoreProperties {

    private String baseUrl = "http://127.0.0.1:8082";
    private String coreToken = "";
    private int connectTimeoutMillis = 5_000;
    private int readTimeoutMillis = 5_000;
    private int generationTimeoutMillis = 180_000;

    public AiRuntimeCoreProperties() {
    }

    public AiRuntimeCoreProperties(
            String baseUrl,
            String coreToken,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            int generationTimeoutMillis) {
        this.baseUrl = baseUrl;
        this.coreToken = coreToken;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.generationTimeoutMillis = generationTimeoutMillis;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCoreToken() {
        return coreToken;
    }

    public void setCoreToken(String coreToken) {
        this.coreToken = coreToken;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getGenerationTimeoutMillis() {
        return generationTimeoutMillis;
    }

    public void setGenerationTimeoutMillis(int generationTimeoutMillis) {
        this.generationTimeoutMillis = generationTimeoutMillis;
    }
}
