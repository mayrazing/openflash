package openflash_admin.dto;

import java.util.List;

public record PlatformAiPageResponse(
    String runtimeStatus,
    boolean runtimeAvailable,
    List<ConnectionResponse> connections
) {
    public PlatformAiPageResponse {
        connections = List.copyOf(connections);
    }
}
