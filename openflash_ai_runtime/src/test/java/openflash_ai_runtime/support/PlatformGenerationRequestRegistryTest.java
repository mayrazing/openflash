package openflash_ai_runtime.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import openflash_ai_runtime.common.RuntimeErrorCode;
import org.junit.jupiter.api.Test;

class PlatformGenerationRequestRegistryTest {

    @Test
    void reserveRejectsDuplicateAndAllowsReuseOnlyAfterExactCompletion() {
        PlatformGenerationRequestRegistry registry = new PlatformGenerationRequestRegistry();
        UUID requestId = UUID.randomUUID();
        var first = registry.reserve(requestId);

        assertThatThrownBy(() -> registry.reserve(requestId))
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(RuntimeErrorCode.INVALID_INTERNAL_REQUEST);

        registry.complete(first);
        var second = registry.reserve(requestId);
        registry.complete(first);
        assertThat(registry.cancel(requestId)).isTrue();
        registry.complete(second);
        assertThat(registry.cancel(requestId)).isFalse();
    }

    @Test
    void cancelBeforeBindIsIrreversibleAndCancelsLateExactHandle() {
        PlatformGenerationRequestRegistry registry = new PlatformGenerationRequestRegistry();
        var state = registry.reserve(UUID.randomUUID());
        AtomicInteger cancellations = new AtomicInteger();

        assertThat(registry.cancel(state.requestId())).isTrue();
        assertThat(registry.bind(state, () -> {
            cancellations.incrementAndGet();
        })).isFalse();

        assertThat(cancellations).hasValue(1);
        assertThat(state.isCancelled()).isTrue();
    }

    @Test
    void cancelAfterBindCallsOnlyTheBoundExactHandle() {
        PlatformGenerationRequestRegistry registry = new PlatformGenerationRequestRegistry();
        var state = registry.reserve(UUID.randomUUID());
        AtomicInteger cancellations = new AtomicInteger();
        assertThat(registry.bind(state, () -> {
            cancellations.incrementAndGet();
        })).isTrue();

        assertThat(registry.cancel(state.requestId())).isTrue();
        assertThat(cancellations).hasValue(1);
        assertThat(registry.cancel(state.requestId())).isTrue();
        assertThat(cancellations).hasValue(1);
    }
}
