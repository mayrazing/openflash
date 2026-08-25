package openflash_core.common;

/** 通用 AI 配置、provider 和模型调用错误码. */
public enum AiErrorCode implements AppErrorCode {

    AI_PROFILE_NOT_CONFIGURED(40051),
    AI_NOT_CONFIGURED(40052),
    AI_MODEL_DISCOVERY_INVALID_URL(40053),
    AI_CODEX_SELECTION_INVALID(40055),
    AI_PROVIDER_RESERVED(40056),
    AI_CODEX_ACCESS_NOT_GRANTED(40302),

    AI_UPSTREAM_UNAVAILABLE(50201),
    AI_EMPTY_RESPONSE(50202),
    AI_INTERRUPTED(50203),
    AI_CONNECTION_FAILED(50204),
    AI_INVALID_RESPONSE(50205),
    AI_MODEL_DISCOVERY_FAILED(50206),
    AI_CODEX_RUNTIME_FAILED(50208),
    AI_CODEX_PROTOCOL_INCOMPATIBLE(50209),
    AI_CODEX_TOOL_BLOCKED(50210),

    AI_PROFILE_UNAVAILABLE(50007),
    AI_KEY_DECRYPT_FAILED(50010);

    private final int value;

    AiErrorCode(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }
}
