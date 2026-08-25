package openflash_admin.dto;

import java.util.List;

public record ConnectionResponse(
    String connectionKey,
    String source,
    String kind,
    String protocol,
    String displayName,
    String baseUrl,
    boolean credentialsConfigured,
    boolean enabled,
    int sortOrder,
    List<OfferingResponse> offerings
) {
    public ConnectionResponse {
        offerings = List.copyOf(offerings);
    }

    public ConnectionResponse(
            String connectionKey, String source, String kind, String protocol,
            String baseUrl, boolean credentialsConfigured, boolean enabled,
            int sortOrder, List<OfferingResponse> offerings) {
        this(connectionKey, source, kind, protocol, null, baseUrl,
            credentialsConfigured, enabled, sortOrder, offerings);
    }
}
