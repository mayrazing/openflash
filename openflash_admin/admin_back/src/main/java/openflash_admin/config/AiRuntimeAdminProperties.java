package openflash_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 定义 admin_back 调用 ai_runtime admin-scope API 的配置. */
@Component
@ConfigurationProperties(prefix = "app.ai-runtime")
public class AiRuntimeAdminProperties {

    private String baseUrl = "http://127.0.0.1:8082";
    private String adminToken = "";
    private int connectTimeoutMillis = 5_000;
    private int readTimeoutMillis = 5_000;

    public AiRuntimeAdminProperties() {
    }

    public AiRuntimeAdminProperties(
            String baseUrl, String adminToken, int connectTimeoutMillis, int readTimeoutMillis) {
        this.baseUrl = baseUrl;
        this.adminToken = adminToken;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
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
}
