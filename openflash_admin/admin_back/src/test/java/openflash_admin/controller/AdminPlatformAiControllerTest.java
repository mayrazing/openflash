package openflash_admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.ConnectionSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.CreateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.CreateOfferingRequest;
import openflash_admin.client.AiRuntimeAdminClient.DiscoveredModel;
import openflash_admin.client.AiRuntimeAdminClient.OfferingSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.ReplaceCredentialsRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetDefaultAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateOfferingRequest;
import openflash_admin.common.AdminExceptionHandler;
import openflash_admin.dto.ConnectionResponse;
import openflash_admin.dto.OfferingResponse;
import openflash_admin.dto.PlatformAiPageResponse;
import openflash_admin.entity.AdminUser;
import openflash_admin.interceptor.AdminAuthInterceptor;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.security.PasswordVerifier;
import openflash_admin.security.AdminLoginAttemptGuard;
import openflash_admin.service.AdminPlatformAiService;
import openflash_admin.service.AdminSessionService;
import openflash_admin.service.impl.AdminSessionServiceImpl;

class AdminPlatformAiControllerTest {

    private static final String INTERNAL_HEADER = "X-OpenFlash-Ai-Runtime-Admin-Token";
    private static final String INTERNAL_TOKEN = "must-never-reach-browser";

    private AdminPlatformAiService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminPlatformAiService.class);
        AdminUserMapper users = mock(AdminUserMapper.class);
        when(users.findById(7L)).thenReturn(admin());
        AdminSessionService sessions = new AdminSessionServiceImpl(
            users, new PasswordVerifier(), AdminLoginAttemptGuard.fromConfigLoader(key -> null));
        mvc = MockMvcBuilders.standaloneSetup(new AdminPlatformAiController(service))
            .addMappedInterceptors(
                new String[] {"/api/admin/**"}, new AdminAuthInterceptor(sessions))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();
    }

    @Test
    void pageWrapsOfflineSafeDatabaseProjectionWithoutInternalFields() throws Exception {
        when(service.page()).thenReturn(new PlatformAiPageResponse(
            "ERROR", false,
            List.of(new ConnectionResponse(
                "platform-api", "PLATFORM", "API", "ANTHROPIC", "https://api.example.test",
                true, true, 0,
                List.of(new OfferingResponse(
                    "platform-model", "PLATFORM", "gpt-5.4", true, false, 0,
                    "ERROR"))))));

        String body = mvc.perform(get("/api/admin/platform-ai").session(session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.runtimeStatus").value("ERROR"))
            .andExpect(jsonPath("$.data.runtimeAvailable").value(false))
            .andExpect(jsonPath("$.data.connections[0].connectionKey")
                .value("platform-api"))
            .andExpect(jsonPath("$.data.connections[0].source").value("PLATFORM"))
            .andExpect(jsonPath("$.data.connections[0].offerings[0].runtimeStatus")
                .value("ERROR"))
            .andExpect(jsonPath("$.data.connections[0].offerings[0].source")
                .value("PLATFORM"))
            .andExpect(header().doesNotExist(INTERNAL_HEADER))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(
            INTERNAL_TOKEN, "apiKey", "secretEnc", "connectionId", "offeringId");
    }

    @Test
    void everyTaskFiveMutationIsMirroredUnderPublicPlatformPath() throws Exception {
        ConnectionSnapshot connection = new ConnectionSnapshot(
            "platform-api", "PLATFORM", "API", "ANTHROPIC", "https://api.example.test",
            true, true, 1, List.of());
        OfferingSnapshot offering = new OfferingSnapshot(
            "platform-model", "PLATFORM", "gpt-5.4", true, false, 2, "AVAILABLE");
        CreateConnectionRequest createConnection = new CreateConnectionRequest(
            "API", "ANTHROPIC", null, "https://api.example.test", 1);
        UpdateConnectionRequest updateConnection = new UpdateConnectionRequest(
            "https://api.example.test", false, 3);
        CreateOfferingRequest createOffering = new CreateOfferingRequest("gpt-5.4", 2);
        UpdateOfferingRequest updateOffering = new UpdateOfferingRequest(
            "gpt-5.4", false, 4);
        when(service.createConnection(createConnection)).thenReturn(connection);
        when(service.updateConnection("platform-api", updateConnection)).thenReturn(connection);
        when(service.discoverModels("platform-api"))
            .thenReturn(List.of(new DiscoveredModel("gpt-5.4")));
        when(service.createOffering("platform-api", createOffering)).thenReturn(offering);
        when(service.updateOffering("platform-model", updateOffering)).thenReturn(offering);

        mvc.perform(post("/api/admin/platform-ai/connections")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"API","protocol":"ANTHROPIC","cliKey":null,
                     "baseUrl":"https://api.example.test","sortOrder":1}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connectionKey").value("platform-api"))
            .andExpect(jsonPath("$.data.source").value("PLATFORM"));
        mvc.perform(put("/api/admin/platform-ai/connections/platform-api")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"baseUrl":"https://api.example.test","enabled":false,"sortOrder":3}
                    """))
            .andExpect(status().isOk());
        String credentialsBody = mvc.perform(put(
                "/api/admin/platform-ai/connections/platform-api/credentials")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"plain-secret\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/admin/platform-ai/connections/platform-api")
                .session(session()))
            .andExpect(status().isOk());
        mvc.perform(post(
                "/api/admin/platform-ai/connections/platform-api/models/discover")
                .session(session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].modelKey").value("gpt-5.4"));
        mvc.perform(post("/api/admin/platform-ai/connections/platform-api/offerings")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelKey\":\"gpt-5.4\",\"sortOrder\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.offeringKey").value("platform-model"))
            .andExpect(jsonPath("$.data.source").value("PLATFORM"));
        mvc.perform(put("/api/admin/platform-ai/offerings/platform-model")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"modelKey":"gpt-5.4","enabled":false,"sortOrder":4}
                    """))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/platform-ai/offerings/platform-model")
                .session(session()))
            .andExpect(status().isOk());
        mvc.perform(put(
                "/api/admin/platform-ai/offerings/platform-model/access/default")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isOk());
        mvc.perform(put(
                "/api/admin/platform-ai/offerings/platform-model/access/users/8")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isOk());
        mvc.perform(delete(
                "/api/admin/platform-ai/offerings/platform-model/access/users/8")
                .session(session()))
            .andExpect(status().isOk());

        assertThat(credentialsBody).doesNotContain("plain-secret", INTERNAL_TOKEN);
        verify(service).createConnection(createConnection);
        verify(service).updateConnection("platform-api", updateConnection);
        verify(service).replaceCredentials(
            "platform-api", new ReplaceCredentialsRequest("plain-secret"));
        verify(service).deleteConnection("platform-api");
        verify(service).discoverModels("platform-api");
        verify(service).createOffering("platform-api", createOffering);
        verify(service).updateOffering("platform-model", updateOffering);
        verify(service).deleteOffering("platform-model");
        verify(service).setDefaultAccess(
            "platform-model", new SetDefaultAccessRequest(true));
        verify(service).setUserAccess(
            "platform-model", 8L, new SetUserAccessRequest(false));
        verify(service).deleteUserAccess("platform-model", 8L);
    }

    @Test
    void unknownRequestFieldsAndNullBodiesAreRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/admin/platform-ai/connections")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"API","protocol":"ANTHROPIC","cliKey":null,
                     "baseUrl":"https://api.example.test","sortOrder":0,
                     "apiKey":"must-not-be-accepted"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40009));
        mvc.perform(put(
                "/api/admin/platform-ai/offerings/platform-model/access/default")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40009));

        verifyNoInteractions(service);
    }

    @Test
    void runtimeDependentWritesReturnSafe50301AndNonAdminsRemainForbidden()
            throws Exception {
        when(service.discoverModels("platform-api"))
            .thenThrow(new AdminRuntimeUnavailableException());
        org.mockito.Mockito.doThrow(new AdminRuntimeUnavailableException())
            .when(service).replaceCredentials(
                "platform-api", new ReplaceCredentialsRequest("plain-secret"));

        mvc.perform(post(
                "/api/admin/platform-ai/connections/platform-api/models/discover")
                .session(session()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
        mvc.perform(put("/api/admin/platform-ai/connections/platform-api/credentials")
                .session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"plain-secret\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301))
            .andExpect(jsonPath("$.data").doesNotExist());

        AdminUserMapper ordinaryUsers = mock(AdminUserMapper.class);
        AdminUser ordinary = admin();
        ordinary.setRole("USER");
        when(ordinaryUsers.findById(7L)).thenReturn(ordinary);
        AdminSessionService ordinarySessions = new AdminSessionServiceImpl(
            ordinaryUsers, new PasswordVerifier(), AdminLoginAttemptGuard.fromConfigLoader(key -> null));
        MockMvc ordinaryMvc = MockMvcBuilders
            .standaloneSetup(new AdminPlatformAiController(service))
            .addMappedInterceptors(
                new String[] {"/api/admin/**"}, new AdminAuthInterceptor(ordinarySessions))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();
        ordinaryMvc.perform(get("/api/admin/platform-ai").session(session()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
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
}
