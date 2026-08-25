package openflash_core.dto;

import openflash_core.common.AiSource;

public record ActiveAiSelectionDto(
        AiSource source,
        String userProviderKey,
        String offeringKey,
        String protocol,
        String model,
        String reasoningEffort,
        String providerInstanceIdentity) {

    public ActiveAiSelectionDto(
            AiSource source,
            String userProviderKey,
            String offeringKey,
            String protocol,
            String model,
            String reasoningEffort) {
        this(source, userProviderKey, offeringKey, protocol, model, reasoningEffort, null);
    }
}
