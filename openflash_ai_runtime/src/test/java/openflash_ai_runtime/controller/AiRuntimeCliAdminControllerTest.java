package openflash_ai_runtime.controller;

import openflash_ai_runtime.client.CodexAppServerClient.StatusCode;
import openflash_ai_runtime.client.CodexAppServerClient.StatusResponse;
import openflash_ai_runtime.support.CodexLoginCoordinator.LoginSnapshot;
import openflash_ai_runtime.support.CodexLoginCoordinator.LoginState;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.common.RuntimeExceptionHandler;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.security.InternalAccessFilter;
import openflash_ai_runtime.security.InternalTokenGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiRuntimeCliAdminControllerTest {

    private static final String ADMIN_TOKEN = "admin-scope-token";
    private static final String CORE_TOKEN = "core-scope-token";

    private CodexRuntimeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CodexRuntimeService.class);
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getInternal().setAdminToken(ADMIN_TOKEN);
        properties.getInternal().setCoreToken(CORE_TOKEN);
        SafeErrorResponseWriter writer = new SafeErrorResponseWriter(new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new AiRuntimeCliAdminController(service))
            .setControllerAdvice(new RuntimeExceptionHandler(writer))
            .addFilters(new InternalAccessFilter(new InternalTokenGuard(properties), writer))
            .build();
    }

    @Test
    void listsAndReadsOnlyTheRegisteredCodexCliThroughTheAdminScope() throws Exception {
        when(service.status()).thenReturn(new StatusResponse(StatusCode.AVAILABLE));
        when(service.loginSnapshot()).thenReturn(
            new LoginSnapshot(LoginState.IDLE, null, null));

        mvc.perform(get("/api/internal/admin/clis")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cliKey").value("codex"))
            .andExpect(jsonPath("$[0].connectionKey").value("platform-codex"))
            .andExpect(jsonPath("$[0].offeringKey").value("platform-codex-cli"))
            .andExpect(jsonPath("$[0].runtimeStatus").value("AVAILABLE"));

        String detail = mvc.perform(get("/api/internal/admin/clis/codex")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cli.cliKey").value("codex"))
            .andExpect(jsonPath("$.login.state").value("IDLE"))
            .andReturn().getResponse().getContentAsString();

        assertThat(detail).doesNotContain(
            "email", "token", "CODEX_HOME", "home", "workdir", "stderr", "rawError");
    }

    @Test
    void rejectsUnknownCliWithSafeNotFoundAndNeverCallsRuntime() throws Exception {
        mvc.perform(get("/api/internal/admin/clis/unknown")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40401))
            .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void loginCancelAndLogoutExposeOnlySafeAllowlistedDtos() throws Exception {
        when(service.startLogin()).thenReturn(CompletableFuture.completedFuture(
            new LoginSnapshot(LoginState.PENDING, "https://example.test/device", "ABCD-EFGH")));
        when(service.cancelLogin()).thenReturn(CompletableFuture.completedFuture(
            new LoginSnapshot(LoginState.CANCELED, null, null)));
        when(service.logoutAccount()).thenReturn(CompletableFuture.completedFuture(true));

        MockMvc currentMvc = mvc;
        MvcResult start = currentMvc.perform(post("/api/internal/admin/clis/codex/login")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(request().asyncStarted()).andReturn();
        currentMvc.perform(asyncDispatch(start))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("PENDING"))
            .andExpect(jsonPath("$.verificationUrl").value("https://example.test/device"))
            .andExpect(jsonPath("$.userCode").value("ABCD-EFGH"));

        MvcResult cancel = currentMvc.perform(delete("/api/internal/admin/clis/codex/login")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(request().asyncStarted()).andReturn();
        currentMvc.perform(asyncDispatch(cancel))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("CANCELED"));

        MvcResult logout = currentMvc.perform(delete("/api/internal/admin/clis/codex/account")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(request().asyncStarted()).andReturn();
        String body = currentMvc.perform(asyncDispatch(logout))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedOut").value(true))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(
            "email", "token", "CODEX_HOME", "home", "workdir", "stderr", "rawError");
    }

    @Test
    void coreScopeTokenCannotReachAdminEndpoints() throws Exception {
        mvc.perform(get("/api/internal/admin/clis")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(service);
    }

    @Test
    void everyAsyncAdminOperationMapsExceptionalCompletionToSafeUnavailable() throws Exception {
        when(service.startLogin()).thenReturn(CompletableFuture.failedFuture(
            new CompletionException(new IllegalStateException("/private login secret"))));
        when(service.cancelLogin()).thenReturn(CompletableFuture.failedFuture(
            new IllegalStateException("cancel stderr")));
        when(service.logoutAccount()).thenReturn(CompletableFuture.failedFuture(
            new IllegalStateException("account token")));

        assertAsyncUnavailable(post("/api/internal/admin/clis/codex/login"));
        assertAsyncUnavailable(delete("/api/internal/admin/clis/codex/login"));
        assertAsyncUnavailable(delete("/api/internal/admin/clis/codex/account"));
    }

    private void assertAsyncUnavailable(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        MvcResult pending = mvc.perform(requestBuilder
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(pending))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private", "secret", "stderr", "token");
    }
}
