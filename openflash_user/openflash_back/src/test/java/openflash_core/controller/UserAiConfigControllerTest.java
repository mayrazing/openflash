package openflash_core.controller;

import openflash_core.service.UserAiConfigService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.ModelOption;
import openflash_core.service.impl.UserAiClientFactory;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl.AiProviderView;
import openflash_core.client.AiRuntimeCoreClient;
import openflash_core.service.CurrentUserService;

class UserAiConfigControllerTest {

    private UnifiedAiSelectionServiceImpl selection;
    private UserAiConfigService configs;
    private UserAiClientFactory clients;
    private AiModelDiscoveryServiceImpl discovery;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        configs = mock(UserAiConfigService.class);
        selection = mock(UnifiedAiSelectionServiceImpl.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        clients = mock(UserAiClientFactory.class);
        discovery = mock(AiModelDiscoveryServiceImpl.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(selection.listProviders(7L)).thenReturn(List.of());
        mvc = MockMvcBuilders.standaloneSetup(new UserAiConfigController(
                configs, selection, currentUser, clients, discovery))
                .build();
    }

    @Test
    void providerListUsesUnifiedSelectionService() throws Exception {
        mvc.perform(get("/api/settings/ai-config/providers")).andExpect(status().isOk());
        verify(selection).listProviders(7L);
    }

    @Test
    void providerListSerializesCompleteUnifiedRowContract() throws Exception {
        when(selection.listProviders(7L)).thenReturn(List.of(new AiProviderView(
                "PLATFORM:platform-cli", "platform-cli", AiSource.PLATFORM,
                "platform-cli", "CLI", "CODEX_APP_SERVER", "settings.platform.name",
                "Platform CLI", "https://platform.test", "shared", null,
                "gpt-5.4", "high", false, true, false, true, true, false, true,
                "AVAILABLE")));

        mvc.perform(get("/api/settings/ai-config/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("PLATFORM:platform-cli"))
                .andExpect(jsonPath("$.data[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.data[0].kind").value("CLI"))
                .andExpect(jsonPath("$.data[0].providerKey").value("platform-cli"))
                .andExpect(jsonPath("$.data[0].offeringKey").value("platform-cli"))
                .andExpect(jsonPath("$.data[0].model").value("gpt-5.4"))
                .andExpect(jsonPath("$.data[0].reasoningEffort").value("high"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].runtimeStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].editable").value(true))
                .andExpect(jsonPath("$.data[0].deletable").value(false))
                .andExpect(jsonPath("$.data[0].accessGranted").value(true));
    }

    @Test
    void personalActivationUsesOnlyUnifiedBoundary() throws Exception {
        mvc.perform(post("/api/settings/ai-config/providers/deepseek/activate")
                        .queryParam("source", "USER"))
                .andExpect(status().isOk());
        verify(selection).activateUserProvider(7L, "deepseek");
        verify(clients).evict(7L);
    }

    @Test
    void createProviderKeepsPersonalConfigFlowAndEvictsCache() throws Exception {
        mvc.perform(post("/api/settings/ai-config/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"DeepSeek","baseUrl":"https://api.deepseek.com",
                                 "apiKey":"sk-new","model":"deepseek-chat"}
                                """))
                .andExpect(status().isOk());

        verify(configs).createProvider(7L, "DeepSeek", null, null,
                "https://api.deepseek.com", "sk-new", "deepseek-chat", null);
        verify(clients).evict(7L);
    }

    @Test
    void saveProviderKeepsPersonalConfigFlowAndEvictsCache() throws Exception {
        mvc.perform(put("/api/settings/ai-config/providers/deepseek")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"DeepSeek","website":"https://deepseek.com",
                                 "note":"primary","baseUrl":"https://api.deepseek.com",
                                 "apiKey":"sk-new","model":"deepseek-chat"}
                                """))
                .andExpect(status().isOk());

        verify(configs).saveProvider(7L, "deepseek", "DeepSeek",
                "https://deepseek.com", "primary", "https://api.deepseek.com",
                "sk-new", "deepseek-chat", null);
        verify(clients).evict(7L);
    }

    @Test
    void saveProviderForwardsPersonalAnthropicReasoningEffort() throws Exception {
        mvc.perform(put("/api/settings/ai-config/providers/deepseek")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"DeepSeek","baseUrl":"https://api.deepseek.com",
                                 "apiKey":"sk-new","model":"deepseek-chat",
                                 "reasoningEffort":"high"}
                                """))
                .andExpect(status().isOk());

        Object[] arguments = mockingDetails(configs).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("saveProvider"))
                .findFirst().orElseThrow().getArguments();
        assertEquals(9, arguments.length);
        assertEquals("high", arguments[8]);
    }

    @Test
    void deleteProviderKeepsPersonalConfigFlowAndEvictsCache() throws Exception {
        mvc.perform(delete("/api/settings/ai-config/providers/deepseek"))
                .andExpect(status().isOk());

        verify(configs).deleteProvider(7L, "deepseek");
        verify(clients).evict(7L);
    }

    @Test
    void discoverModelsUsesSubmittedPersonalKey() throws Exception {
        when(configs.resolveDiscoveryApiKey(7L, null, "sk-new")).thenReturn("sk-new");
        when(discovery.discover("https://api.anthropic.com", "sk-new"))
                .thenReturn(List.of(new ModelOption("claude-sonnet", "claude-sonnet")));

        mvc.perform(post("/api/settings/ai-config/models/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://api.anthropic.com","apiKey":"sk-new"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("claude-sonnet"));

        verify(configs).resolveDiscoveryApiKey(7L, null, "sk-new");
        verify(discovery).discover("https://api.anthropic.com", "sk-new");
    }

    @Test
    void discoverModelsReusesCurrentUsersSavedPersonalKey() throws Exception {
        when(configs.resolveDiscoveryApiKey(7L, "deepseek", null)).thenReturn("saved-key");
        when(discovery.discover("https://api.deepseek.com/anthropic", "saved-key"))
                .thenReturn(List.of(new ModelOption("deepseek-v4-flash", "deepseek-v4-flash")));

        mvc.perform(post("/api/settings/ai-config/models/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerKey":"deepseek",
                                 "baseUrl":"https://api.deepseek.com/anthropic","apiKey":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("deepseek-v4-flash"));

        verify(configs).resolveDiscoveryApiKey(7L, "deepseek", null);
        verify(discovery).discover("https://api.deepseek.com/anthropic", "saved-key");
    }

    @Test
    void platformPreferenceSaveDoesNotActivate() throws Exception {
        mvc.perform(put("/api/settings/ai-config/platform-offerings/platform-codex-cli/preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5.4\",\"reasoningEffort\":\"high\"}"))
                .andExpect(status().isOk());
        verify(selection).savePlatformCliPreference(
                7L, "platform-codex-cli", "gpt-5.4", "high");
    }

    @Test
    void platformActivationUsesExplicitOfferingPath() throws Exception {
        mvc.perform(post("/api/settings/ai-config/platform-offerings/platform-codex-cli/activate"))
                .andExpect(status().isOk());
        verify(selection).activatePlatformOffering(7L, "platform-codex-cli");
    }

    @Test
    void platformModelsUsesFixedOfferingPath() throws Exception {
        when(selection.listPlatformModels(7L, "platform-codex-cli"))
                .thenReturn(new AiRuntimeCoreClient.ModelsSnapshot("ERROR", List.of()));
        mvc.perform(get("/api/settings/ai-config/platform-offerings/platform-codex-cli/models"))
                .andExpect(status().isOk());
        verify(selection).listPlatformModels(7L, "platform-codex-cli");
    }
}
