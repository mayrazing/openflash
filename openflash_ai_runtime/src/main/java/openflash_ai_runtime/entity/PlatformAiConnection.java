package openflash_ai_runtime.entity;

/** 平台账号或 CLI 连接的非敏感元数据. */
public final class PlatformAiConnection {
    private long id;
    private final String connectionKey;
    private final String kind;
    private final String protocol;
    private final String cliKey;
    private final String displayName;
    private final String baseUrl;
    private final boolean credentialsConfigured;
    private final boolean enabled;
    private final int sortOrder;

    public PlatformAiConnection(
            long id, String connectionKey, String kind, String protocol, String cliKey,
            String displayName, String baseUrl, boolean credentialsConfigured,
            boolean enabled, int sortOrder) {
        this.id = id;
        this.connectionKey = connectionKey;
        this.kind = kind;
        this.protocol = protocol;
        this.cliKey = cliKey;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.credentialsConfigured = credentialsConfigured;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    public PlatformAiConnection(
            long id, String connectionKey, String kind, String protocol, String cliKey,
            String baseUrl, boolean credentialsConfigured, boolean enabled, int sortOrder) {
        this(id, connectionKey, kind, protocol, cliKey, null, baseUrl,
                credentialsConfigured, enabled, sortOrder);
    }

    public long id() { return id; }
    public String connectionKey() { return connectionKey; }
    public String kind() { return kind; }
    public String protocol() { return protocol; }
    public String cliKey() { return cliKey; }
    public String displayName() { return displayName; }
    public String baseUrl() { return baseUrl; }
    public boolean credentialsConfigured() { return credentialsConfigured; }
    public boolean enabled() { return enabled; }
    public int sortOrder() { return sortOrder; }
    public void setId(long id) { this.id = id; }
    public long getId() { return id; }
    public String getConnectionKey() { return connectionKey; }
    public String getKind() { return kind; }
    public String getProtocol() { return protocol; }
    public String getCliKey() { return cliKey; }
    public String getDisplayName() { return displayName; }
    public String getBaseUrl() { return baseUrl; }
    public boolean isCredentialsConfigured() { return credentialsConfigured; }
    public boolean isEnabled() { return enabled; }
    public int getSortOrder() { return sortOrder; }
}
