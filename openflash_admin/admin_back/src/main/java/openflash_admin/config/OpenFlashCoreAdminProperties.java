package openflash_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 定义 admin_back 调用 openflash_back 账号级联 API 的配置. */
@Component
@ConfigurationProperties(prefix = "app.openflash-core-admin")
public class OpenFlashCoreAdminProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    private String token = "";
    private int connectTimeoutMillis = 5_000;
    private int readTimeoutMillis = 5_000;

    public OpenFlashCoreAdminProperties() {
    }

    public OpenFlashCoreAdminProperties(
            String baseUrl, String token, int connectTimeoutMillis, int readTimeoutMillis) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
