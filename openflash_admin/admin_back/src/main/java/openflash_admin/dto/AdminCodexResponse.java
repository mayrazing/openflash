package openflash_admin.dto;

import openflash_admin.client.AiRuntimeAdminClient.LoginSnapshot;

public record AdminCodexResponse(
    boolean enabled,
    String runtimeStatus,
    LoginSnapshot login,
    int globalChangeMaxDelaySeconds
) {
}
