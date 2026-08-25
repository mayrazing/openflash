package openflash_plugin.ai_card.common;

import openflash_core.common.AppErrorCode;

/**
 * AI 卡片插件专属错误码，仅覆盖卡片解释、补全和异步任务业务。
 * 实现 core 的 AppErrorCode 契约，随插件目录存放，新增/删除不触碰 core。
 * value 沿用历史值，保证前端编码不变。
 */
public enum AiCardErrorCode implements AppErrorCode {

    // ── 卡片业务 400xx ──
    AI_CARD_SIDE_BLANK(40050),
    AI_EXPLANATION_DISABLED(40054),

    // ── 内部异步 500xx（不传前端，仅日志）──
    ASYNC_AI_TASK_PARSE_FAILED(50001),
    ASYNC_SIDE_COMPLETION_PARSE_FAILED(50002),
    ASYNC_AI_TASK_MISSING_USER_ID(50005),
    ASYNC_SIDE_COMPLETION_MISSING_USER_ID(50006);

    private final int value;

    AiCardErrorCode(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }
}
