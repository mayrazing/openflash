package openflash_admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.ConnectionSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.CreateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.CreateOfferingRequest;
import openflash_admin.client.AiRuntimeAdminClient.DiscoveredModel;
import openflash_admin.client.AiRuntimeAdminClient.OfferingSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.PlatformAiPageSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.ReplaceCredentialsRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetDefaultAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateOfferingRequest;
import openflash_admin.dto.ConnectionResponse;
import openflash_admin.dto.OfferingResponse;
import openflash_admin.dto.PlatformAiPageResponse;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.CatalogRow;
import openflash_admin.service.AdminPlatformAiService;

class AdminPlatformAiServiceImplTest {

    @Test
    void offlinePageKeepsDatabaseSafeMetadataAndMarksRuntimeUnavailable() {
        Fixture fixture = fixture();
        when(fixture.mapper.findCatalogRows()).thenReturn(List.of(
            apiRow(true, false, true),
            codexRow(false, false)));
        when(fixture.runtime.platformAiPage()).thenThrow(new AdminRuntimeUnavailableException());

        PlatformAiPageResponse page = fixture.service.page();

        assertThat(page.runtimeAvailable()).isFalse();
        assertThat(page.runtimeStatus()).isEqualTo("ERROR");
        assertThat(page.connections()).containsExactly(
            new ConnectionResponse(
                "platform-api", "PLATFORM", "API", "ANTHROPIC", "https://db.example.test",
                true, true, 7,
                List.of(new OfferingResponse(
                    "platform-model", "PLATFORM", "gpt-5.4", false, true, 9,
                    "ERROR"))),
            new ConnectionResponse(
                "platform-codex", "PLATFORM", "CLI", "CODEX_APP_SERVER", null,
                false, false, 2,
                List.of(new OfferingResponse(
                    "platform-codex-cli", "PLATFORM", null, false, false, 0,
                    "ERROR"))));
        assertThat(page.toString()).doesNotContain("secret", "cipher", "apiKey");
    }

    @Test
    void onlinePageKeepsDatabaseMetadataAndOverlaysOnlySafeRuntimeState() {
        Fixture fixture = fixture();
        when(fixture.mapper.findCatalogRows()).thenReturn(List.of(
            apiRow(false, true, false)));
        when(fixture.runtime.platformAiPage()).thenReturn(new PlatformAiPageSnapshot(
            "AVAILABLE",
            List.of(new ConnectionSnapshot(
                "platform-api", "PLATFORM", "API", "ANTHROPIC",
                "https://runtime-must-not-win.test",
                true, false, 999,
                List.of(new OfferingSnapshot(
                    "platform-model", "PLATFORM", "runtime-model-must-not-win", true, false,
                    999, "AVAILABLE"))))));

        PlatformAiPageResponse page = fixture.service.page();

        assertThat(page.runtimeAvailable()).isTrue();
        assertThat(page.runtimeStatus()).isEqualTo("AVAILABLE");
        ConnectionResponse connection = page.connections().get(0);
        assertThat(connection.baseUrl()).isEqualTo("https://db.example.test");
        assertThat(connection.enabled()).isTrue();
        assertThat(connection.sortOrder()).isEqualTo(7);
        assertThat(connection.credentialsConfigured()).isTrue();
        assertThat(connection.offerings().get(0)).isEqualTo(
            new OfferingResponse(
                "platform-model", "PLATFORM", "gpt-5.4", true, false, 9,
                "AVAILABLE"));
    }

    @Test
    void authAndProgrammingErrorsAreNotSwallowedAsOffline() {
        Fixture fixture = fixture();
        when(fixture.mapper.findCatalogRows()).thenReturn(List.of(apiRow(true, true, false)));
        when(fixture.runtime.platformAiPage())
            .thenThrow(new AdminException(AdminErrorCode.FORBIDDEN));

        assertThatThrownBy(fixture.service::page)
            .isInstanceOf(AdminException.class)
            .satisfies(failure -> assertThat(((AdminException) failure).getErrorCode())
                .isEqualTo(AdminErrorCode.FORBIDDEN));

        RuntimeException databaseFailure = new IllegalStateException("database failed");
        when(fixture.mapper.findCatalogRows()).thenThrow(databaseFailure);
        assertThatThrownBy(fixture.service::page).isSameAs(databaseFailure);
    }

    @Test
    void everyWriteDelegatesExactlyOnceToRuntimeAndNeverWritesDatabase() {
        Fixture fixture = fixture();
        CreateConnectionRequest createConnection = new CreateConnectionRequest(
            "API", "ANTHROPIC", null, "https://api.example.test", 1);
        UpdateConnectionRequest updateConnection = new UpdateConnectionRequest(
            "https://api.example.test", false, 2);
        ReplaceCredentialsRequest credentials = new ReplaceCredentialsRequest("plain-secret");
        CreateOfferingRequest createOffering = new CreateOfferingRequest("gpt-5.4", 3);
        UpdateOfferingRequest updateOffering = new UpdateOfferingRequest(
            "gpt-5.4", false, 4);
        SetDefaultAccessRequest defaultAccess = new SetDefaultAccessRequest(true);
        SetUserAccessRequest userAccess = new SetUserAccessRequest(false);
        ConnectionSnapshot connection = new ConnectionSnapshot(
            "platform-api", "PLATFORM", "API", "ANTHROPIC", "https://api.example.test",
            false, true, 1, List.of());
        OfferingSnapshot offering = new OfferingSnapshot(
            "platform-model", "PLATFORM", "gpt-5.4", true, false, 3, "UNAVAILABLE");
        when(fixture.runtime.createConnection(createConnection)).thenReturn(connection);
        when(fixture.runtime.updateConnection("platform-api", updateConnection))
            .thenReturn(connection);
        when(fixture.runtime.discoverModels("platform-api"))
            .thenReturn(List.of(new DiscoveredModel("gpt-5.4")));
        when(fixture.runtime.createOffering("platform-api", createOffering))
            .thenReturn(offering);
        when(fixture.runtime.updateOffering("platform-model", updateOffering))
            .thenReturn(offering);

        assertThat(fixture.service.createConnection(createConnection)).isEqualTo(connection);
        assertThat(fixture.service.updateConnection("platform-api", updateConnection))
            .isEqualTo(connection);
        fixture.service.replaceCredentials("platform-api", credentials);
        fixture.service.deleteConnection("platform-api");
        assertThat(fixture.service.discoverModels("platform-api"))
            .containsExactly(new DiscoveredModel("gpt-5.4"));
        assertThat(fixture.service.createOffering("platform-api", createOffering))
            .isEqualTo(offering);
        assertThat(fixture.service.updateOffering("platform-model", updateOffering))
            .isEqualTo(offering);
        fixture.service.deleteOffering("platform-model");
        fixture.service.setDefaultAccess("platform-model", defaultAccess);
        fixture.service.setUserAccess("platform-model", 8L, userAccess);
        fixture.service.deleteUserAccess("platform-model", 8L);

        verify(fixture.runtime).createConnection(createConnection);
        verify(fixture.runtime).updateConnection("platform-api", updateConnection);
        verify(fixture.runtime).replaceCredentials("platform-api", credentials);
        verify(fixture.runtime).deleteConnection("platform-api");
        verify(fixture.runtime).discoverModels("platform-api");
        verify(fixture.runtime).createOffering("platform-api", createOffering);
        verify(fixture.runtime).updateOffering("platform-model", updateOffering);
        verify(fixture.runtime).deleteOffering("platform-model");
        verify(fixture.runtime).setDefaultAccess("platform-model", defaultAccess);
        verify(fixture.runtime).setUserAccess("platform-model", 8L, userAccess);
        verify(fixture.runtime).deleteUserAccess("platform-model", 8L);
        verify(fixture.mapper, never()).findEnabledOfferings();
    }

    @Test
    void writeUnavailablePropagates503WithoutFallbackWrite() {
        Fixture fixture = fixture();
        ReplaceCredentialsRequest request = new ReplaceCredentialsRequest("plain-secret");
        doThrow(new AdminRuntimeUnavailableException())
            .when(fixture.runtime).replaceCredentials("platform-api", request);

        assertThatThrownBy(() -> fixture.service.replaceCredentials("platform-api", request))
            .isInstanceOf(AdminRuntimeUnavailableException.class);

        verifyNoInteractions(fixture.mapper);
    }

    private static Fixture fixture() {
        AdminPlatformAiMapper mapper = mock(AdminPlatformAiMapper.class);
        AiRuntimeAdminClient runtime = mock(AiRuntimeAdminClient.class);
        return new Fixture(mapper, runtime, new AdminPlatformAiServiceImpl(mapper, runtime));
    }

    private static CatalogRow apiRow(
            boolean credentialsConfigured,
            boolean offeringEnabled,
            boolean defaultAccess) {
        return new CatalogRow(
            11L, "platform-api", "API", "ANTHROPIC", null,
            "https://db.example.test", credentialsConfigured, true, 7,
            21L, "platform-model", "gpt-5.4", offeringEnabled, defaultAccess, 9);
    }

    private static CatalogRow codexRow(
            boolean connectionEnabled,
            boolean offeringEnabled) {
        return new CatalogRow(
            12L, "platform-codex", "CLI", "CODEX_APP_SERVER", "codex",
            null, false, connectionEnabled, 2,
            22L, "platform-codex-cli", null, offeringEnabled, false, 0);
    }

    private record Fixture(
        AdminPlatformAiMapper mapper,
        AiRuntimeAdminClient runtime,
        AdminPlatformAiService service
    ) {
    }
}
