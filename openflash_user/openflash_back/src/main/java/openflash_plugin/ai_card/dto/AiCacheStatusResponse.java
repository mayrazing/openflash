package openflash_plugin.ai_card.dto;

import openflash_core.common.AppErrorCode;

/**
 * 返回给前端的 AI 缓存状态。
 */
public class AiCacheStatusResponse {

    private static final String STATUS_HIT = "hit";
    private static final String STATUS_QUEUED = "queued";
    private static final String STATUS_DISABLED = "disabled";

    private final String status;
    private final String content;
    private final Integer errorCode;
    private final Boolean sideCompletionSetupRequired;

    private AiCacheStatusResponse(String status, String content, Integer errorCode,
            Boolean sideCompletionSetupRequired) {
        this.status = status;
        this.content = content;
        this.errorCode = errorCode;
        this.sideCompletionSetupRequired = sideCompletionSetupRequired;
    }

    public static AiCacheStatusResponse hit(String content) {
        return hit(content, false);
    }

    public static AiCacheStatusResponse hit(String content, boolean sideCompletionSetupRequired) {
        return new AiCacheStatusResponse(STATUS_HIT, content, null, sideCompletionSetupRequired);
    }

    public static AiCacheStatusResponse queued() {
        return queued(false);
    }

    public static AiCacheStatusResponse queued(boolean sideCompletionSetupRequired) {
        return new AiCacheStatusResponse(STATUS_QUEUED, null, null, sideCompletionSetupRequired);
    }

    public static AiCacheStatusResponse disabled(AppErrorCode errorCode, boolean sideCompletionSetupRequired) {
        return new AiCacheStatusResponse(STATUS_DISABLED, null, errorCode.value(), sideCompletionSetupRequired);
    }

    public String getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public Boolean getSideCompletionSetupRequired() {
        return sideCompletionSetupRequired;
    }
}
