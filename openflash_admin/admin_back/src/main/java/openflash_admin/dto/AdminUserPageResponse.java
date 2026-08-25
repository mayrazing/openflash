package openflash_admin.dto;

import java.util.List;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;

public record AdminUserPageResponse(
    boolean runtimeAvailable,
    List<CliSnapshot> clis,
    List<OfferingAccessMetadata> offerings,
    List<AdminUserResponse> users
) {
}
