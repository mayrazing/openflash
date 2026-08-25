package openflash_ai_runtime.entity;

/** 一个可授权给用户的平台 AI 入口. */
public final class PlatformAiOffering {
    private long id;
    private final long connectionId;
    private final String offeringKey;
    private final String modelKey;
    private final boolean enabled;
    private final boolean defaultAccess;
    private final int sortOrder;

    public PlatformAiOffering(
            long id, long connectionId, String offeringKey, String modelKey,
            boolean enabled, boolean defaultAccess, int sortOrder) {
        this.id = id;
        this.connectionId = connectionId;
        this.offeringKey = offeringKey;
        this.modelKey = modelKey;
        this.enabled = enabled;
        this.defaultAccess = defaultAccess;
        this.sortOrder = sortOrder;
    }

    public long id() { return id; }
    public long connectionId() { return connectionId; }
    public String offeringKey() { return offeringKey; }
    public String modelKey() { return modelKey; }
    public boolean enabled() { return enabled; }
    public boolean defaultAccess() { return defaultAccess; }
    public int sortOrder() { return sortOrder; }
    public void setId(long id) { this.id = id; }
    public long getId() { return id; }
    public long getConnectionId() { return connectionId; }
    public String getOfferingKey() { return offeringKey; }
    public String getModelKey() { return modelKey; }
    public boolean isEnabled() { return enabled; }
    public boolean isDefaultAccess() { return defaultAccess; }
    public int getSortOrder() { return sortOrder; }
}
