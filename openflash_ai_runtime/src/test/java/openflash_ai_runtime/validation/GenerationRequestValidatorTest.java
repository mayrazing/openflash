package openflash_ai_runtime.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import openflash_ai_runtime.common.RuntimeErrorCode;
import org.junit.jupiter.api.Test;

class GenerationRequestValidatorTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Test
    void acceptsEveryExactBoundaryAndFiniteTemperatureEndpoints() {
        assertThatCode(() -> GenerationRequestValidator.validatePlatform(
                REQUEST_ID, 1L, "o".repeat(255), "m".repeat(255), "e".repeat(64),
                "p".repeat(200_000), "s".repeat(100_000), 0.0)).doesNotThrowAnyException();
        assertThatCode(() -> GenerationRequestValidator.validatePlatform(
                REQUEST_ID, 1L, "offering", "model", null,
                "prompt", null, 1.0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOversizeIdentityPromptAndSystemFields() {
        assertInvalid(() -> validate("o".repeat(256), "model", null, "prompt", null, 0.2));
        assertInvalid(() -> validate("offering", "m".repeat(256), null, "prompt", null, 0.2));
        assertInvalid(() -> validate("offering", "model", "e".repeat(65), "prompt", null, 0.2));
        assertInvalid(() -> validate(
                "offering", "model", null, "p".repeat(200_001), null, 0.2));
        assertInvalid(() -> validate(
                "offering", "model", null, "prompt", "s".repeat(100_001), 0.2));
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeTemperatures() {
        assertInvalid(() -> validate("offering", "model", null, "prompt", null, -0.01));
        assertInvalid(() -> validate("offering", "model", null, "prompt", null, 1.01));
        assertInvalid(() -> validate("offering", "model", null, "prompt", null, Double.NaN));
        assertInvalid(() -> validate(
                "offering", "model", null, "prompt", null, Double.POSITIVE_INFINITY));
        assertInvalid(() -> validate(
                "offering", "model", null, "prompt", null, Double.NEGATIVE_INFINITY));
    }

    private static void validate(
            String offering, String model, String effort, String prompt,
            String systemPrompt, Double temperature) {
        GenerationRequestValidator.validatePlatform(
                REQUEST_ID, 1L, offering, model, effort, prompt, systemPrompt, temperature);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }
}
