package openflash_plugin.tts.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import openflash_core.common.AppErrorCode;
import openflash_core.common.AppException;
import org.junit.jupiter.api.Test;

/** 守护 TTS 插件错误码 value（沿用历史值，前端编码不变）及其作为 AppErrorCode 的可传递性。 */
class TtsErrorCodeTest {

    @Test
    void ttsUpstreamErrorHasCorrectValue() {
        assertEquals(50207, TtsErrorCode.TTS_UPSTREAM_ERROR.value());
    }

    @Test
    void ttsTextBlankHasCorrectValue() {
        assertEquals(40040, TtsErrorCode.TTS_TEXT_BLANK.value());
    }

    @Test
    void isAppErrorCodeAndThrowableViaAppException() {
        AppErrorCode code = TtsErrorCode.TTS_TEXT_BLANK;
        AppException ex = new AppException(code);
        assertSame(code, ex.getErrorCode());
    }
}
