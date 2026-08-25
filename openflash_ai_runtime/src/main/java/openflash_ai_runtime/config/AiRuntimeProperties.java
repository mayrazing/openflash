package openflash_ai_runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AiRuntimeProperties {

    private final Internal internal = new Internal();
    private final PlatformSecret platformSecret = new PlatformSecret();

    public Internal getInternal() {
        return internal;
    }

    public PlatformSecret getPlatformSecret() {
        return platformSecret;
    }

    public static class Internal {

        private String adminToken = "";
        private String coreToken = "";

        public String getAdminToken() {
            return adminToken;
        }

        public void setAdminToken(String adminToken) {
            this.adminToken = adminToken;
        }

        public String getCoreToken() {
            return coreToken;
        }

        public void setCoreToken(String coreToken) {
            this.coreToken = coreToken;
        }
    }

    public static class PlatformSecret {

        private String password = "";
        private String salt = "";

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }
    }
}
