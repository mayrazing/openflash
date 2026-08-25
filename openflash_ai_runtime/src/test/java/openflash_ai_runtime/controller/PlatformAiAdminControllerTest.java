package openflash_ai_runtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.common.RuntimeExceptionHandler;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.security.InternalAccessFilter;
import openflash_ai_runtime.security.InternalTokenGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PlatformAiAdminControllerTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String CORE_TOKEN = "core-token";
    private PlatformAiCatalogService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PlatformAiCatalogService.class);
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getInternal().setAdminToken(ADMIN_TOKEN);
        properties.getInternal().setCoreToken(CORE_TOKEN);
        SafeErrorResponseWriter writer = new SafeErrorResponseWriter(new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new PlatformAiAdminController(service))
                .setControllerAdvice(new RuntimeExceptionHandler(writer))
                .setMessageConverters(strictJsonConverter())
                .addFilters(new InternalAccessFilter(new InternalTokenGuard(properties), writer))
                .build();
    }

    @Test
    void pageAndMutationsExposeExactSafeDtoWithoutSecretsOrInternalIds() throws Exception {
        PlatformAiCatalogService.OfferingView offering = new PlatformAiCatalogService.OfferingView(
                "platform-api-model", "gpt-5.4", true, false, 0, "AVAILABLE",
                "API", "ANTHROPIC");
        PlatformAiCatalogService.ConnectionView connection =
                new PlatformAiCatalogService.ConnectionView(
                        "platform-api", "API", "ANTHROPIC",
                        "https://api.example.test", true, true, 0, List.of(offering));
        when(service.page()).thenReturn(
                new PlatformAiCatalogService.PageView("AVAILABLE", List.of(connection)));
        when(service.createConnection(any())).thenReturn(connection);
        when(service.updateConnection(any(), any())).thenReturn(connection);
        when(service.createOffering(any(), any())).thenReturn(offering);
        when(service.updateOffering(any(), any())).thenReturn(offering);
        when(service.discoverModels("platform-api"))
                .thenReturn(List.of("gpt-5.4"));

        String page = mvc.perform(get("/api/internal/admin/platform-ai")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.connections[0].connectionKey")
                        .value("platform-api"))
                .andExpect(jsonPath("$.connections[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.connections[0].credentialsConfigured").value(true))
                .andExpect(jsonPath("$.connections[0].offerings[0].source")
                        .value("PLATFORM"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain(
                "secret", "apiKey", "secretEnc", "connectionId", "offeringId");

        mvc.perform(post("/api/internal/admin/platform-ai/connections")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"kind":"API","protocol":"ANTHROPIC","cliKey":null,
                             "baseUrl":"https://api.example.test","sortOrder":0}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionKey").value("platform-api"))
                .andExpect(jsonPath("$.source").value("PLATFORM"));
        mvc.perform(put("/api/internal/admin/platform-ai/connections/platform-api")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"baseUrl":"https://api.example.test","enabled":true,
                             "sortOrder":0}
                            """))
                .andExpect(status().isOk());
        mvc.perform(put("/api/internal/admin/platform-ai/connections/platform-api/credentials")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"plain-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("plain-secret"));
        mvc.perform(post("/api/internal/admin/platform-ai/connections/platform-api/models/discover")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelKey").value("gpt-5.4"));
        mvc.perform(post("/api/internal/admin/platform-ai/connections/platform-api/offerings")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelKey\":\"gpt-5.4\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PLATFORM"));
        mvc.perform(put("/api/internal/admin/platform-ai/offerings/platform-api-model")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"modelKey":"gpt-5.4","enabled":true,"sortOrder":0}
                            """))
                .andExpect(status().isOk());
        mvc.perform(put("/api/internal/admin/platform-ai/offerings/platform-api-model/access/default")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/internal/admin/platform-ai/offerings/platform-api-model/access/users/5")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/internal/admin/platform-ai/offerings/platform-api-model/access/users/5")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/internal/admin/platform-ai/offerings/platform-api-model")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/internal/admin/platform-ai/connections/platform-api")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk());

        verify(service).replaceCredentials("platform-api", "plain-secret");
        verify(service).setDefaultAccess("platform-api-model", true);
        verify(service).setUserAccess("platform-api-model", 5L, false);
        verify(service).deleteUserAccess("platform-api-model", 5L);
    }

    @Test
    void coreTokenCannotReachAdminApiAndUnknownFieldsAreRejected() throws Exception {
        mvc.perform(get("/api/internal/admin/platform-ai")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/internal/admin/platform-ai/connections")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"kind":"API","protocol":"ANTHROPIC","cliKey":null,
                             "baseUrl":"https://api.example.test","sortOrder":0,
                             "apiKey":"must-not-be-accepted-here"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/internal/admin/platform-ai/connections")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(service);
    }

    private static JacksonJsonHttpMessageConverter strictJsonConverter() {
        return new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }
}
