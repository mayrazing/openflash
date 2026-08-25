package openflash_core.service.impl;

import openflash_core.common.AiSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import openflash_core.entity.UserAiConfig;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.entity.PlatformAiOffering;
import openflash_core.entity.UserActiveAiSelection;
import openflash_core.entity.UserPlatformAiPreference;
import openflash_core.mapper.PlatformAiOfferingMapper;
import openflash_core.mapper.PlatformAiUserAccessMapper;
import openflash_core.mapper.UserActiveAiSelectionMapper;
import openflash_core.mapper.UserPlatformAiPreferenceMapper;
import openflash_core.client.AiRuntimeCoreClient;

class UnifiedAiSelectionServiceImplTest {

    private UserAiConfigMapper users;
    private PlatformAiOfferingMapper offerings;
    private UserPlatformAiPreferenceMapper preferences;
    private UserActiveAiSelectionMapper active;
    private AiRuntimeCoreClient runtime;
    private UnifiedAiSelectionServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserAiConfigMapper.class);
        offerings = mock(PlatformAiOfferingMapper.class);
        preferences = mock(UserPlatformAiPreferenceMapper.class);
        active = mock(UserActiveAiSelectionMapper.class);
        runtime = mock(AiRuntimeCoreClient.class);
        service = new UnifiedAiSelectionServiceImpl(
                users, offerings, mock(PlatformAiUserAccessMapper.class), preferences, active, runtime);
    }

    @Test
    void personalAndPlatformActivationWriteOneExplicitSourceRow() {
        when(users.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(user("deepseek", "ANTHROPIC", "deepseek-chat"));
        PlatformAiOffering codex = offering(31L, "platform-codex-cli", null, true, true);
        when(offerings.findByKeyAndUserId("platform-codex-cli", 7L)).thenReturn(codex);
        when(preferences.find(7L, 31L))
                .thenReturn(new UserPlatformAiPreference(7L, 31L, "gpt-5.4", "high"));

        service.activateUserProvider(7L, "deepseek");
        service.activatePlatformOffering(7L, "platform-codex-cli");

        verify(active).upsert(new UserActiveAiSelection(7L, AiSource.USER, "deepseek", null));
        verify(active).upsert(new UserActiveAiSelection(7L, AiSource.PLATFORM, null, 31L));
    }

    @Test
    void savingMultipleCliPreferencesNeverChangesActiveSelection() {
        PlatformAiOffering first = offering(31L, "platform-codex-cli", null, true, true);
        PlatformAiOffering second = offering(32L, "platform-codex-next", null, true, true);
        when(offerings.findByKeyAndUserId("platform-codex-cli", 7L)).thenReturn(first);
        when(offerings.findByKeyAndUserId("platform-codex-next", 7L)).thenReturn(second);
        when(runtime.listModels(7L, "platform-codex-cli")).thenReturn(models("gpt-5.4", "high"));
        when(runtime.listModels(7L, "platform-codex-next")).thenReturn(models("gpt-5.5", "medium"));

        service.savePlatformCliPreference(7L, "platform-codex-cli", "gpt-5.4", "high");
        service.savePlatformCliPreference(7L, "platform-codex-next", "gpt-5.5", "medium");

        verify(preferences).upsert(new UserPlatformAiPreference(7L, 31L, "gpt-5.4", "high"));
        verify(preferences).upsert(new UserPlatformAiPreference(7L, 32L, "gpt-5.5", "medium"));
        verify(active, never()).upsert(any());
    }

    @Test
    void revokeOrDisablePreservesSelectionButMakesItUnusableAndRestoresOnRegrant() {
        when(active.findByUserId(7L))
                .thenReturn(new UserActiveAiSelection(7L, AiSource.PLATFORM, null, 31L));
        when(preferences.find(7L, 31L))
                .thenReturn(new UserPlatformAiPreference(7L, 31L, "gpt-5.4", "high"));
        when(offerings.findByIdAndUserId(31L, 7L))
                .thenReturn(offering(31L, "platform-codex-cli", null, true, false))
                .thenReturn(offering(31L, "platform-codex-cli", null, false, true))
                .thenReturn(offering(31L, "platform-codex-cli", null, true, true));

        assertFalse(service.isActiveSelectionUsable(7L));
        assertFalse(service.isActiveSelectionUsable(7L));
        assertTrue(service.isActiveSelectionUsable(7L));
        verify(active, never()).deleteByUserId(7L);
    }

    @Test
    void fixedApiOfferingAlwaysUsesRegisteredModel() {
        when(active.findByUserId(7L))
                .thenReturn(new UserActiveAiSelection(7L, AiSource.PLATFORM, null, 40L));
        when(offerings.findByIdAndUserId(40L, 7L))
                .thenReturn(offering(40L, "platform-anthropic-sonnet", "claude-sonnet", true, true));
        when(preferences.find(7L, 40L))
                .thenReturn(new UserPlatformAiPreference(7L, 40L, "forged", "high"));

        assertEquals("claude-sonnet", service.requireActive(7L).model());
    }

    @Test
    void personalProviderEndpointAndCredentialProduceDistinctInstanceIdentity() {
        UserAiConfig first = userWithConnection(
                "deepseek", "https://api-a.example", "enc-key-a");
        UserAiConfig second = userWithConnection(
                "deepseek", "https://api-b.example", "enc-key-b");
        when(active.findByUserId(7L))
                .thenReturn(new UserActiveAiSelection(7L, AiSource.USER, "deepseek", null));
        when(users.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(first, second);

        String firstIdentity = service.requireActive(7L).providerInstanceIdentity();
        String secondIdentity = service.requireActive(7L).providerInstanceIdentity();

        assertNotEquals(firstIdentity, secondIdentity);
    }

    @Test
    void personalProviderExposesAndActivatesItsSavedReasoningEffort() {
        UserAiConfig personal = user("deepseek", "ANTHROPIC", "deepseek-chat");
        personal.setConfigJson("""
                {"protocol":"ANTHROPIC","model":"deepseek-chat",
                 "reasoningEffort":"high"}
                """);
        when(users.findAllByUserId(7L)).thenReturn(List.of(personal));
        when(active.findByUserId(7L))
                .thenReturn(new UserActiveAiSelection(7L, AiSource.USER, "deepseek", null));
        when(users.findByUserIdAndProvider(7L, "deepseek")).thenReturn(personal);

        assertEquals("high", service.listProviders(7L).get(0).reasoningEffort());
        assertEquals("high", service.requireActive(7L).reasoningEffort());
    }

    @Test
    void runtimeOfflineDoesNotFallbackOrMutateSelection() {
        when(active.findByUserId(7L))
                .thenReturn(new UserActiveAiSelection(7L, AiSource.PLATFORM, null, 31L));
        when(offerings.findByIdAndUserId(31L, 7L))
                .thenReturn(offering(31L, "platform-codex-cli", null, true, true));
        when(preferences.find(7L, 31L))
                .thenReturn(new UserPlatformAiPreference(7L, 31L, "gpt-5.4", "high"));

        assertEquals(AiSource.PLATFORM, service.requireActive(7L).source());
        verify(active, never()).deleteByUserId(7L);
        verify(active, never()).upsert(any());
    }

    @Test
    void providerViewsExposeStableIdentityAndEditabilityBySourceAndKind() {
        UserAiConfig personal = user("deepseek", "ANTHROPIC", "deepseek-chat");
        PlatformAiOffering fixedApi = new PlatformAiOffering(
                40L, "platform-fixed", "gpt-5.4", true, false, 0,
                "API", "OPENAI_RESPONSES", true, true);
        PlatformAiOffering cli = offering(41L, "platform-cli", null, true, true);
        when(users.findAllByUserId(7L)).thenReturn(List.of(personal));
        when(offerings.findVisibleByUserId(7L)).thenReturn(List.of(fixedApi, cli));
        when(preferences.find(7L, 41L))
                .thenReturn(new UserPlatformAiPreference(7L, 41L, "gpt-cli", "high"));
        when(runtime.listOfferings(7L)).thenReturn(List.of(
                new AiRuntimeCoreClient.OfferingSnapshot(
                        "platform-fixed", AiSource.PLATFORM, "API", "OPENAI_RESPONSES",
                        "gpt-5.4", "AVAILABLE", true, true),
                new AiRuntimeCoreClient.OfferingSnapshot(
                        "platform-cli", AiSource.PLATFORM, "CLI", "CODEX_APP_SERVER",
                        null, "AVAILABLE", true, true)));

        List<UnifiedAiSelectionServiceImpl.AiProviderView> views = service.listProviders(7L);

        assertEquals(List.of("USER:deepseek", "PLATFORM:platform-fixed", "PLATFORM:platform-cli"),
                views.stream().map(UnifiedAiSelectionServiceImpl.AiProviderView::id).toList());
        assertTrue(views.get(0).editable());
        assertFalse(views.get(1).editable());
        assertTrue(views.get(2).editable());
    }

    @Test
    void emptyAndOutOfRangeInputsFailBeforeWrites() {
        assertThrows(RuntimeException.class, () -> service.activateUserProvider(0L, "deepseek"));
        assertThrows(RuntimeException.class, () -> service.activatePlatformOffering(7L, " "));
        assertThrows(RuntimeException.class,
                () -> service.savePlatformCliPreference(7L, "platform-codex-cli", "", "high"));
        verify(active, never()).upsert(any());
        verify(preferences, never()).upsert(any());
    }

    private static UserAiConfig user(String key, String protocol, String model) {
        UserAiConfig row = new UserAiConfig();
        row.setUserId(7L);
        row.setProvider(key);
        row.setConfigJson("{\"protocol\":\"" + protocol + "\",\"model\":\"" + model + "\"}");
        return row;
    }

    private static UserAiConfig userWithConnection(String key, String baseUrl, String apiKeyEnc) {
        UserAiConfig row = new UserAiConfig();
        row.setUserId(7L);
        row.setProvider(key);
        row.setConfigJson("{\"protocol\":\"ANTHROPIC\",\"model\":\"m\","
                + "\"baseUrl\":\"" + baseUrl + "\",\"apiKeyEnc\":\"" + apiKeyEnc + "\"}");
        return row;
    }

    private static PlatformAiOffering offering(
            long id, String key, String model, boolean enabled, boolean accessGranted) {
        return new PlatformAiOffering(
                id, key, model, enabled, false, 0, "CLI", "CODEX_APP_SERVER", true,
                accessGranted);
    }

    private static AiRuntimeCoreClient.ModelsSnapshot models(String model, String effort) {
        return new AiRuntimeCoreClient.ModelsSnapshot("AVAILABLE", List.of(
                new AiRuntimeCoreClient.ModelSnapshot(
                        model, model, model, "", true, effort,
                        List.of(new AiRuntimeCoreClient.ReasoningEffortSnapshot(effort, effort)))));
    }
}
