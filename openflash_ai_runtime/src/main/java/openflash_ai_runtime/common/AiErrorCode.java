package openflash_ai_runtime.common;

/** 区分 Codex 内部失败类型, 不直接序列化到内部 HTTP 响应. */
public enum AiErrorCode {
    AI_CODEX_SELECTION_INVALID,
    AI_EMPTY_RESPONSE,
    AI_INTERRUPTED,
    AI_CODEX_RUNTIME_FAILED,
    AI_CODEX_PROTOCOL_INCOMPATIBLE,
    AI_CODEX_TOOL_BLOCKED
}
