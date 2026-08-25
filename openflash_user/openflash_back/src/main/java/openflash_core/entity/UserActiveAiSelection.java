package openflash_core.entity;

import openflash_core.common.AiSource;

public record UserActiveAiSelection(
        Long userId, AiSource source, String userProviderKey, Long offeringId) {
}
