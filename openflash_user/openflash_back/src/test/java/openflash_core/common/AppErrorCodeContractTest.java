package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * 验证 core 错误码抽象契约：AppException / AppLog 依赖 AppErrorCode 接口，
 * 而非具体 ErrorCode 枚举，从而允许插件自带错误码而无需改 core。
 */
class AppErrorCodeContractTest {

    /** 模拟插件侧自定义错误码——core 不认识它，但必须能接受。 */
    private static final AppErrorCode PLUGIN_CODE = new AppErrorCode() {
        @Override
        public int value() {
            return 49999;
        }

        @Override
        public String name() {
            return "PLUGIN_DEFINED_CODE";
        }
    };

    @Test
    void appExceptionAcceptsAnyAppErrorCode() {
        AppException ex = new AppException(PLUGIN_CODE);

        assertSame(PLUGIN_CODE, ex.getErrorCode());
        assertEquals(49999, ex.getErrorCode().value());
    }

    @Test
    void coreErrorCodeImplementsAppErrorCode() {
        // core 自带枚举必须也是 AppErrorCode，保证统一传递通道。
        AppErrorCode code = ErrorCode.GENERIC_ERROR;

        assertEquals(50000, code.value());
        assertEquals("GENERIC_ERROR", code.name());
    }
}
