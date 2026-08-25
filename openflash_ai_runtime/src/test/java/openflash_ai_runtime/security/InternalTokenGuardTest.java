package openflash_ai_runtime.security;

import openflash_ai_runtime.common.RuntimeException;
import openflash_ai_runtime.config.AiRuntimeProperties;
import org.junit.jupiter.api.Test;

import static openflash_ai_runtime.security.InternalTokenGuard.Scope.ADMIN;
import static openflash_ai_runtime.security.InternalTokenGuard.Scope.CORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenGuardTest {

    private static final String ADMIN_TOKEN = "admin-test-token";
    private static final String CORE_TOKEN = "core-test-token";

    @Test
    void acceptsOnlyTheTokenConfiguredForTheRequestedScope() {
        InternalTokenGuard guard = guard(ADMIN_TOKEN, CORE_TOKEN);

        assertThat(guard.requireAdmin(ADMIN_TOKEN)).isEqualTo(ADMIN);
        assertThat(guard.requireCore(CORE_TOKEN)).isEqualTo(CORE);
        assertThatThrownBy(() -> guard.requireAdmin(CORE_TOKEN)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> guard.requireCore(ADMIN_TOKEN)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsMissingRequestTokens() {
        InternalTokenGuard guard = guard(ADMIN_TOKEN, CORE_TOKEN);

        assertThatThrownBy(() -> guard.requireAdmin(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> guard.requireAdmin("")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> guard.requireCore(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> guard.requireCore("")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsRequestsWhenTheServerTokenForThatScopeIsEmpty() {
        assertThatThrownBy(() -> guard("", CORE_TOKEN).requireAdmin(""))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> guard(ADMIN_TOKEN, "").requireCore(""))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void exposesDifferentExactHeadersForAdminAndCore() {
        assertThat(InternalTokenGuard.ADMIN_TOKEN_HEADER)
            .isEqualTo("X-OpenFlash-Ai-Runtime-Admin-Token");
        assertThat(InternalTokenGuard.CORE_TOKEN_HEADER)
            .isEqualTo("X-OpenFlash-Ai-Runtime-Core-Token");
        assertThat(InternalTokenGuard.ADMIN_TOKEN_HEADER)
            .isNotEqualTo(InternalTokenGuard.CORE_TOKEN_HEADER);
    }

    private InternalTokenGuard guard(String adminToken, String coreToken) {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getInternal().setAdminToken(adminToken);
        properties.getInternal().setCoreToken(coreToken);
        return new InternalTokenGuard(properties);
    }

}
