package openflash_core.entity;

public record UserPlatformAiPreference(
        Long userId, Long offeringId, String model, String reasoningEffort) {
}
