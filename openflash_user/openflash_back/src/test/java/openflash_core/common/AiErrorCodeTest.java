package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiErrorCodeTest {

    @Test
    void genericAiErrorsKeepPublishedNumericValues() {
        assertEquals(40051, AiErrorCode.AI_PROFILE_NOT_CONFIGURED.value());
        assertEquals(40052, AiErrorCode.AI_NOT_CONFIGURED.value());
        assertEquals(40053, AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL.value());
        assertEquals(40055, AiErrorCode.AI_CODEX_SELECTION_INVALID.value());
        assertEquals(40056, AiErrorCode.AI_PROVIDER_RESERVED.value());
        assertEquals(50201, AiErrorCode.AI_UPSTREAM_UNAVAILABLE.value());
        assertEquals(50202, AiErrorCode.AI_EMPTY_RESPONSE.value());
        assertEquals(50203, AiErrorCode.AI_INTERRUPTED.value());
        assertEquals(50204, AiErrorCode.AI_CONNECTION_FAILED.value());
        assertEquals(50205, AiErrorCode.AI_INVALID_RESPONSE.value());
        assertEquals(50206, AiErrorCode.AI_MODEL_DISCOVERY_FAILED.value());
        assertEquals(50208, AiErrorCode.AI_CODEX_RUNTIME_FAILED.value());
        assertEquals(50209, AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE.value());
        assertEquals(50210, AiErrorCode.AI_CODEX_TOOL_BLOCKED.value());
        assertEquals(50007, AiErrorCode.AI_PROFILE_UNAVAILABLE.value());
        assertEquals(50010, AiErrorCode.AI_KEY_DECRYPT_FAILED.value());
    }
}
