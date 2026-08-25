package openflash_plugin.ai_card.dto;

/** 承载卡包 AI 设置页一次保存的全部字段。 */
public record DeckAiSettingsUpdateCommand(
        Boolean aiExplanationEnabledA,
        Boolean aiExplanationEnabledB,
        String aiExplanationPromptA,
        String aiExplanationPromptB,
        Boolean aiCompletionEnabled,
        String aiCompletionPrompt) {
}
