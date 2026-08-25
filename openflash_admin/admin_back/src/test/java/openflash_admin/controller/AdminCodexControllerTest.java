package openflash_admin.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.CliAdminSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.ConnectionSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.LoginSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.common.AdminExceptionHandler;
import openflash_admin.entity.AdminUser;
import openflash_admin.interceptor.AdminAuthInterceptor;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.CatalogRow;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.security.PasswordVerifier;
import openflash_admin.security.AdminLoginAttemptGuard;
import openflash_admin.service.AdminSessionService;
import openflash_admin.service.impl.AdminCodexServiceImpl;
import openflash_admin.service.impl.AdminSessionServiceImpl;

class AdminCodexControllerTest {

    @Test
    void snapshotCombinesDatabaseCatalogEnabledStateWithRuntimeSafeProjection()
            throws Exception {
        Fixture fixture = fixture(codexRow(true, false));
        when(fixture.runtime.codexSnapshot()).thenReturn(new CliAdminSnapshot(
                new CliSnapshot("codex", "platform-codex", "platform-codex-cli", "AVAILABLE"),
                new LoginSnapshot("IDLE", null, null)));

        fixture.mvc.perform(get("/api/admin/codex").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.runtimeStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.login.state").value("IDLE"))
                .andExpect(jsonPath("$.data.globalChangeMaxDelaySeconds").value(60))
                .andExpect(jsonPath("$.data.connectionKey").doesNotExist());

        verify(fixture.mapper).findCliOffering("codex");
    }

    @Test
    void offlineSnapshotStillReturnsReadyDtoButWritesReturnSafe50301() throws Exception {
        Fixture fixture = fixture(codexRow(true, true));
        when(fixture.runtime.codexSnapshot()).thenThrow(new AdminRuntimeUnavailableException());
        when(fixture.runtime.startCodexLogin()).thenThrow(new AdminRuntimeUnavailableException());
        when(fixture.runtime.cancelCodexLogin()).thenThrow(new AdminRuntimeUnavailableException());
        org.mockito.Mockito.doThrow(new AdminRuntimeUnavailableException())
                .when(fixture.runtime).logoutCodexAccount();

        fixture.mvc.perform(get("/api/admin/codex").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.runtimeStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.login.state").value("FAILED"));

        fixture.mvc.perform(post("/api/admin/codex/login").session(session()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
        fixture.mvc.perform(delete("/api/admin/codex/login").session(session()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
        fixture.mvc.perform(delete("/api/admin/codex/account").session(session()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void runtimeAuthAndDatabaseErrorsAreNotMisreportedAsOffline() throws Exception {
        Fixture fixture = fixture(codexRow(true, true));
        when(fixture.runtime.codexSnapshot())
                .thenThrow(new AdminException(AdminErrorCode.FORBIDDEN));

        fixture.mvc.perform(get("/api/admin/codex").session(session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        RuntimeException databaseFailure = new IllegalStateException("database failed");
        when(fixture.mapper.findCliOffering("codex")).thenThrow(databaseFailure);
        fixture.mvc.perform(get("/api/admin/codex").session(session()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000));
    }

    @Test
    void loginCancelAndLogoutDelegateToRuntimeOnly() throws Exception {
        Fixture fixture = fixture(codexRow(false, false));
        when(fixture.runtime.startCodexLogin())
                .thenReturn(new LoginSnapshot("PENDING", "https://auth.test/device", "ABCD"));
        when(fixture.runtime.cancelCodexLogin())
                .thenReturn(new LoginSnapshot("CANCELED", null, null));

        fixture.mvc.perform(post("/api/admin/codex/login").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PENDING"));
        fixture.mvc.perform(delete("/api/admin/codex/login").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CANCELED"));
        fixture.mvc.perform(delete("/api/admin/codex/account").session(session()))
                .andExpect(status().isOk());

        verify(fixture.runtime).startCodexLogin();
        verify(fixture.runtime).cancelCodexLogin();
        verify(fixture.runtime).logoutCodexAccount();
        verifyNoInteractions(fixture.mapper);
    }

    @Test
    void enabledUpdateUsesDatabaseConnectionMetadataAndOneRuntimeWrite() throws Exception {
        CatalogRow row = codexRow(false, false);
        Fixture fixture = fixture(row);
        when(fixture.runtime.updateConnection(
                "platform-codex", new UpdateConnectionRequest(null, true, 2)))
                .thenReturn(new ConnectionSnapshot(
                        "platform-codex", "PLATFORM", "CLI", "CODEX_APP_SERVER", null,
                        false, true, 2, List.of()));

        fixture.mvc.perform(put("/api/admin/codex/enabled")
                .session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(fixture.mapper).findCliOffering("codex");
        verify(fixture.runtime).updateConnection(
                "platform-codex", new UpdateConnectionRequest(null, true, 2));
    }

    @Test
    void missingCatalogConnectionCannotBeToggledButSnapshotRemainsReadable()
            throws Exception {
        Fixture fixture = fixture(null);
        when(fixture.runtime.codexSnapshot()).thenReturn(new CliAdminSnapshot(
                new CliSnapshot("codex", "platform-codex", "platform-codex-cli", "NOT_INSTALLED"),
                new LoginSnapshot("IDLE", null, null)));

        fixture.mvc.perform(get("/api/admin/codex").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        fixture.mvc.perform(put("/api/admin/codex/enabled")
                .session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        verify(fixture.runtime, never()).updateConnection(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedToggleAndMissingAdminSessionAreRejected() throws Exception {
        Fixture fixture = fixture(codexRow(false, false));

        fixture.mvc.perform(put("/api/admin/codex/enabled")
                .session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"featureKey\":\"feature.tts\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
        fixture.mvc.perform(get("/api/admin/codex"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private static Fixture fixture(CatalogRow codexRow) {
        AdminUser admin = admin();
        AdminUserMapper users = mock(AdminUserMapper.class);
        when(users.findById(7L)).thenReturn(admin);
        AdminSessionService sessions = new AdminSessionServiceImpl(
            users, new PasswordVerifier(), AdminLoginAttemptGuard.fromConfigLoader(key -> null));
        AdminPlatformAiMapper mapper = mock(AdminPlatformAiMapper.class);
        when(mapper.findCliOffering("codex")).thenReturn(codexRow);
        AiRuntimeAdminClient runtime = mock(AiRuntimeAdminClient.class);
        AdminCodexServiceImpl service = new AdminCodexServiceImpl(mapper, runtime);
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AdminCodexController(service))
                .addMappedInterceptors(
                        new String[] { "/api/admin/**" }, new AdminAuthInterceptor(sessions))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
        return new Fixture(mvc, mapper, runtime);
    }

    private static CatalogRow codexRow(
            boolean connectionEnabled,
            boolean offeringEnabled) {
        return new CatalogRow(
                12L, "platform-codex", "CLI", "CODEX_APP_SERVER", "codex",
                null, null, false, connectionEnabled, 2,
                22L, "platform-codex-cli", null, offeringEnabled, false, 0);
    }

    private static MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminUserId", 7L);
        session.setAttribute("adminAuthVersion", 0L);
        return session;
    }

    private static AdminUser admin() {
        AdminUser user = new AdminUser();
        user.setId(7L);
        user.setUsername("root-admin");
        user.setRole("ADMIN");
        user.setAdminApproved(true);
        user.setBanned(0);
        user.setAuthVersion(0L);
        user.setDeleted(0);
        return user;
    }

    private record Fixture(
            MockMvc mvc,
            AdminPlatformAiMapper mapper,
            AiRuntimeAdminClient runtime) {
    }
}
