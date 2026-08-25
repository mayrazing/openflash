package openflash_ai_runtime.entity;

/** 用户对一个 offering 的显式授权覆盖. */
public record PlatformAiUserAccess(long userId, long offeringId, boolean enabled) {
}
