package openflash_core.common;

/**
 * core 通用错误码。仅包含与具体插件无关的核心域错误（认证/卡片/卡包/设置/练习/上传/导入/异步框架）。
 * 插件专属错误码（TTS/AI 等）由各插件自带枚举 implements {@link AppErrorCode}，不再集中于此。
 */
public enum ErrorCode implements AppErrorCode {

    // ── 认证 401xx ──────────────────────────────────────────
    UNAUTHORIZED(40101),
    SESSION_EXPIRED(40102),
    ACCOUNT_BANNED(40103),
    ACCOUNT_DELETED(40104),

    // ── 授权 403xx ──────────────────────────────────────────
    FORBIDDEN(40301),

    // ── 注册登录 400xx ───────────────────────────────────────
    USERNAME_TAKEN(40001),
    WRONG_CREDENTIALS(40002),
    USERNAME_BLANK(40003),
    USERNAME_LENGTH_INVALID(40004),
    PASSWORD_BLANK(40005),
    PASSWORD_LENGTH_INVALID(40006),
    NICKNAME_TOO_LONG(40007),
    CURRENT_PASSWORD_INCORRECT(40008),
    LOGIN_RATE_LIMITED(42902),

    // ── 卡片 ─────────────────────────────────────────────────
    CARD_ALREADY_EXISTS(40010),
    CARD_NOT_FOUND(40011),

    // ── 卡包 ─────────────────────────────────────────────────
    DECK_NOT_FOUND(40020),
    DECK_NAME_BLANK(40021),
    DECK_MOVE_TARGET_INVALID(40022),

    // ── 用户设置 ──────────────────────────────────────────────
    USER_SETTINGS_NOT_FOUND(40030),
    STUDY_INTENSITY_INVALID(40031),
    DECK_SETTINGS_INVALID(40032),
    UNSUPPORTED_LANGUAGE(40033),

    // ── 练习 ─────────────────────────────────────────────────
    PRACTICE_DIRECTION_INVALID(40060),
    PRACTICE_MODE_INVALID(40061),
    PRACTICE_RATING_INVALID(40062),
    PRACTICE_STATE_INVALID(40063),

    // ── 上传 ─────────────────────────────────────────────────
    UPLOAD_FILE_MISSING(40080),
    UPLOAD_FILE_NOT_IMAGE(40081),
    UPLOAD_MEDIA_ACCESS_DENIED(40082),

    // ── 导入 ─────────────────────────────────────────────────
    IMPORT_BACKUP_BLANK(40070),
    IMPORT_BACKUP_NO_DATA(40071),
    IMPORT_DECK_FILE_BLANK(40072),
    IMPORT_DECK_FILE_MISSING_DECKS_JSON(40073),
    IMPORT_ZIP_LIMIT_EXCEEDED(40074),

    // ── 功能/配置 503xx ───────────────────────────────────────
    FEATURE_DISABLED(50301),
    PLUGIN_NOT_SUPPORTED(50302),

    // ── 内部异步/系统 500xx（不传前端，仅日志） ───────────────────
    ASYNC_UNKNOWN_TASK_TYPE(50003),
    INTERNAL_USER_NOT_FOUND(50004),
    UPLOAD_FILE_DELETE_FAILED(50008),

    // ── 浏览器导入 ─────────────────────────────────────────────
    BROWSER_IMPORT_INVALID_IMAGE_URL(40090),
    BROWSER_IMPORT_EMPTY_CONTENT(40091),
    BROWSER_IMPORT_IMAGE_TRANSFER_FAILED(40092),

    // ── 内部管理 API ─────────────────────────────────────────
    INTERNAL_ADMIN_REQUEST_INVALID(40093),
    USER_NOT_FOUND(40401),
    LAST_ADMIN_REQUIRED(40901),
    SELF_ACCOUNT_MUTATION(40902),

    // ── 通用兜底 ─────────────────────────────────────────────
    GENERIC_ERROR(50000);

    private final int value;

    ErrorCode(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }
}
