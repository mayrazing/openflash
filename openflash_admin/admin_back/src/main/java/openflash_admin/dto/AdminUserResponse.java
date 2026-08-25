package openflash_admin.dto;

import java.util.Map;

public record AdminUserResponse(
    Long id,
    String username,
    String nickname,
    String role,
    Boolean banned,
    Map<String, Boolean> cliAccess,
    Map<String, Boolean> offeringAccess
) {
}
