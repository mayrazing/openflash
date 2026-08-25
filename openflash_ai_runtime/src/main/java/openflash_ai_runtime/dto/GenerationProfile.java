package openflash_ai_runtime.dto;

/** 保存单次 Codex 生成所需的语言无关参数. */
public record GenerationProfile(
        String model,
        String systemPrompt,
        Double temperature,
        String reasoningEffort) {

    @Override
    public String toString() {
        return "GenerationProfile[model=" + model
                + ", systemPrompt=<redacted>, temperature=" + temperature
                + ", reasoningEffort=" + reasoningEffort + "]";
    }
}
