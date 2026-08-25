package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import openflash_core.config.AiProperties;
import openflash_core.service.impl.EffectiveAiProfileResolver.ActiveAiIdentity;
import openflash_core.common.AiSource;

class CardAiPromptSupportTest {

    @Test
    void fingerprintIgnoresEveryActiveAiIdentityDimension() {
        ActiveAiIdentity baseline = identity(
                AiSource.PLATFORM, "platform-codex-cli", "gpt-5.4", "high",
                profile("card-ai", "gpt-5.4", "explain", 0.2));

        assertEquals(fingerprint(baseline), fingerprint(identity(
                AiSource.USER, "platform-codex-cli", "gpt-5.4", "high",
                profile("card-ai", "gpt-5.4", "explain", 0.2))));
        assertEquals(fingerprint(baseline), fingerprint(identity(
                AiSource.PLATFORM, "platform-codex-api", "gpt-5.4", "high",
                profile("card-ai", "gpt-5.4", "explain", 0.2))));
        assertEquals(fingerprint(baseline), fingerprint(identity(
                AiSource.PLATFORM, "platform-codex-cli", "gpt-5.3", "high",
                profile("card-ai", "gpt-5.4", "explain", 0.2))));
        assertEquals(fingerprint(baseline), fingerprint(identity(
                AiSource.PLATFORM, "platform-codex-cli", "gpt-5.4", "medium",
                profile("card-ai", "gpt-5.4", "explain", 0.2))));
        assertEquals(fingerprint(baseline), fingerprint(identity(
                AiSource.PLATFORM, "platform-codex-cli", "gpt-5.4", "high",
                profile("card-ai", "gpt-5.4", "translate", 0.2))));
    }

    @Test
    void fingerprintIgnoresProfileFieldBoundaries() {
        ActiveAiIdentity first = identity(
                AiSource.USER, "openai-main", "gpt-5.4", null,
                profile("card\nai", "gpt-5.4", "system", 0.2));
        ActiveAiIdentity sameValues = identity(
                AiSource.USER, "openai-main", "gpt-5.4", null,
                profile("card\nai", "gpt-5.4", "system", 0.2));
        ActiveAiIdentity shiftedBoundary = identity(
                AiSource.USER, "openai-main", "gpt-5.4", null,
                profile("card", "ai\ngpt-5.4", "system", 0.2));

        assertEquals(fingerprint(first), fingerprint(sameValues));
        assertEquals(fingerprint(first), fingerprint(shiftedBoundary));
    }

    @Test
    void fingerprintIgnoresUserAndProviderInstance() {
        AiProperties.AiProfile profile = profile("card-ai", "gpt-5.4", "system", 0.2);
        ActiveAiIdentity baseline = new ActiveAiIdentity(
                7L, AiSource.USER, "openai-main", "gpt-5.4", null,
                "endpoint-and-key-a", profile);

        assertEquals(fingerprint(baseline), fingerprint(new ActiveAiIdentity(
                8L, AiSource.USER, "openai-main", "gpt-5.4", null,
                "endpoint-and-key-a", profile)));
        assertEquals(fingerprint(baseline), fingerprint(new ActiveAiIdentity(
                7L, AiSource.USER, "openai-main", "gpt-5.4", null,
                "endpoint-and-key-b", profile)));
    }

    @Test
    void fingerprintChangesWhenTargetContentChanges() {
        assertNotEquals(
                CardAiPromptSupport.buildFingerprint("apple"),
                CardAiPromptSupport.buildFingerprint("pear"));
    }

    private static String fingerprint(ActiveAiIdentity identity) {
        return CardAiPromptSupport.buildFingerprint("apple", identity);
    }

    private static ActiveAiIdentity identity(
            AiSource source,
            String selectionKey,
            String model,
            String reasoningEffort,
            AiProperties.AiProfile profile) {
        return new ActiveAiIdentity(source, selectionKey, model, reasoningEffort, profile);
    }

    private static AiProperties.AiProfile profile(
            String name, String model, String system, double temperature) {
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setName(name);
        profile.setModel(model);
        profile.setSystem(system);
        profile.setTemperature(temperature);
        return profile;
    }
}
