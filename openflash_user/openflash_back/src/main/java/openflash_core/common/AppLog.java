package openflash_core.common;

import org.slf4j.Logger;

/** 统一日志工具，自动在格式串前注入 [E:CODE_NAME:12345]，确保所有内部错误日志可按 code 溯源。 */
public class AppLog {

    private AppLog() {}

    /** 记录 error 级别日志，格式：[E:CODE_NAME:value] msg */
    public static void error(Logger log, AppErrorCode code, String msg, Object... args) {
        log.error(prefix(code) + msg, prepend(code, args));
    }

    /** 记录 warn 级别日志，格式：[E:CODE_NAME:value] msg */
    public static void warn(Logger log, AppErrorCode code, String msg, Object... args) {
        log.warn(prefix(code) + msg, prepend(code, args));
    }

    private static String prefix(AppErrorCode code) {
        return "[E:" + code.name() + ":" + code.value() + "] ";
    }

    private static Object[] prepend(AppErrorCode code, Object[] rest) {
        return rest;
    }
}
