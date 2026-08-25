package openflash_ai_runtime.validation;

import java.util.UUID;
import openflash_ai_runtime.common.RuntimeErrorCode;

/** 对所有 runtime 生成入口应用同一组字符与数值硬上限. */
public final class GenerationRequestValidator {

    public static final int MAX_OFFERING_KEY_CHARACTERS = 255;
    public static final int MAX_MODEL_CHARACTERS = 255;
    public static final int MAX_REASONING_EFFORT_CHARACTERS = 64;
    public static final int MAX_PROMPT_CHARACTERS = 200_000;
    public static final int MAX_SYSTEM_PROMPT_CHARACTERS = 100_000;
    public static final int MAX_JSON_BODY_BYTES = 512 * 1024;

    private GenerationRequestValidator() {
    }

    public static void validatePlatform(
            UUID requestId,
            long userId,
            String offeringKey,
            String model,
            String reasoningEffort,
            String prompt,
            String systemPrompt,
            Double temperature) {
        if (requestId == null
                || userId <= 0L
                || invalidRequired(offeringKey, MAX_OFFERING_KEY_CHARACTERS)
                || invalidRequired(model, MAX_MODEL_CHARACTERS)
                || invalidOptional(reasoningEffort, MAX_REASONING_EFFORT_CHARACTERS)
                || invalidRequired(prompt, MAX_PROMPT_CHARACTERS)
                || invalidOptional(systemPrompt, MAX_SYSTEM_PROMPT_CHARACTERS)
                || invalidTemperature(temperature)) {
            throw invalidRequest();
        }
    }

    public static void validateTransport(
            UUID requestId,
            String model,
            String prompt,
            String systemPrompt,
            Double temperature) {
        if (requestId == null
                || invalidRequired(model, MAX_MODEL_CHARACTERS)
                || invalidRequired(prompt, MAX_PROMPT_CHARACTERS)
                || invalidOptional(systemPrompt, MAX_SYSTEM_PROMPT_CHARACTERS)
                || invalidTemperature(temperature)) {
            throw invalidRequest();
        }
    }

    public static void validateCodex(
            UUID requestId,
            String model,
            String reasoningEffort,
            String prompt,
            String systemPrompt,
            Double temperature) {
        if (requestId == null
                || invalidRequired(model, MAX_MODEL_CHARACTERS)
                || invalidRequired(reasoningEffort, MAX_REASONING_EFFORT_CHARACTERS)
                || invalidRequired(prompt, MAX_PROMPT_CHARACTERS)
                || invalidOptional(systemPrompt, MAX_SYSTEM_PROMPT_CHARACTERS)
                || invalidTemperature(temperature)) {
            throw invalidRequest();
        }
    }

    private static boolean invalidRequired(String value, int maxCharacters) {
        return value == null || value.isBlank() || value.length() > maxCharacters;
    }

    private static boolean invalidOptional(String value, int maxCharacters) {
        return value != null && value.length() > maxCharacters;
    }

    private static boolean invalidTemperature(Double temperature) {
        return temperature != null
                && (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 1.0);
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }
}
