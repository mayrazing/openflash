package openflash_core.common;

/**
 * 错误码抽象契约。core 的 AppException / AppLog 只依赖此接口，不绑定具体枚举，
 * 从而让插件在自己目录内声明专属错误码（implements AppErrorCode），无需回改 core。
 */
public interface AppErrorCode {

    /** 错误码数值，最终透传给前端并参与 HTTP 状态映射（value/100）。 */
    int value();

    /** 错误码名称，用于日志溯源；enum 实现自动满足。 */
    String name();
}
