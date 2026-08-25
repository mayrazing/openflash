package openflash_plugin.ai_card.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import openflash_core.common.AppErrorCode;
import openflash_core.common.AppException;
import org.junit.jupiter.api.Test;

/** 守护 AI 卡片插件错误码 value（沿用历史值，前端编码不变）及其作为 AppErrorCode 的可传递性。 */
class AiCardErrorCodeTest {

    @Test
    void cardBusinessErrorsKeepPublishedValues() {
        assertEquals(40050, AiCardErrorCode.AI_CARD_SIDE_BLANK.value());
        assertEquals(40054, AiCardErrorCode.AI_EXPLANATION_DISABLED.value());
        assertEquals(50001, AiCardErrorCode.ASYNC_AI_TASK_PARSE_FAILED.value());
        assertEquals(50002, AiCardErrorCode.ASYNC_SIDE_COMPLETION_PARSE_FAILED.value());
        assertEquals(50005, AiCardErrorCode.ASYNC_AI_TASK_MISSING_USER_ID.value());
        assertEquals(50006, AiCardErrorCode.ASYNC_SIDE_COMPLETION_MISSING_USER_ID.value());
    }

    @Test
    void isAppErrorCodeAndThrowableViaAppException() {
        AppErrorCode code = AiCardErrorCode.AI_EXPLANATION_DISABLED;
        AppException ex = new AppException(code);
        assertSame(code, ex.getErrorCode());
    }
}
