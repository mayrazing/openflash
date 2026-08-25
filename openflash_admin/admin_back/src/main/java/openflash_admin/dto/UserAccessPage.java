package openflash_admin.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;

public record UserAccessPage(
    boolean runtimeAvailable,
    List<CliSnapshot> clis,
    List<OfferingAccessMetadata> offerings,
    Map<Long, UserAccess> accessByUserId
) {
    public UserAccessPage {
        clis = List.copyOf(clis);
        offerings = List.copyOf(offerings);
        accessByUserId = Collections.unmodifiableMap(new LinkedHashMap<>(accessByUserId));
    }
}
