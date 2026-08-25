package openflash_ai_runtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.entity.PlatformAiConnection;
import openflash_ai_runtime.entity.PlatformAiOffering;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper.UsableOfferingRow;
import openflash_ai_runtime.mapper.PlatformAiUserAccessMapper;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformSecretService;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.transport.PlatformAiTransport;
import openflash_ai_runtime.transport.PlatformAiTransportRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

class PlatformAiCatalogServiceImplTest {

    private PlatformAiConnectionMapper connections;
    private PlatformAiOfferingMapper offerings;
    private PlatformAiUserAccessMapper access;
    private PlatformSecretService secrets;
    private PlatformAiTransportRegistry transports;
    private CodexRuntimeService codex;
    private PlatformGenerationRequestRegistry requestRegistry;
    private PlatformAiCatalogService service;

    @BeforeEach
    void setUp() {
        connections = mock(PlatformAiConnectionMapper.class);
        offerings = mock(PlatformAiOfferingMapper.class);
        access = mock(PlatformAiUserAccessMapper.class);
        secrets = mock(PlatformSecretService.class);
        transports = mock(PlatformAiTransportRegistry.class);
        codex = mock(CodexRuntimeService.class);
        requestRegistry = new PlatformGenerationRequestRegistry();
        service = new PlatformAiCatalogServiceImpl(
                connections, offerings, access, secrets, transports, codex,
                requestRegistry);
    }

    @Test
    void defaultDenyAndDisabledOrDeletedOfferingsNeverAppearInCoreList() {
        when(offerings.findUsableByUserId(41L)).thenReturn(List.of(apiUsable()));

        assertThat(service.listUsableOfferings(41L))
                .extracting(PlatformAiCatalogService.OfferingView::offeringKey)
                .containsExactly("platform-api-model");
        verify(offerings).findUsableByUserId(41L);

        when(offerings.findUsableByUserId(42L)).thenReturn(List.of());
        assertThat(service.listUsableOfferings(42L)).isEmpty();
    }

    @Test
    void mapperResolvesOverrideBeforeDefaultAndFiltersBothEnabledBoundaries()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/mapper/PlatformAiOfferingMapper.xml"));
        assertThat(sql)
                .contains("a.user_id = #{userId}")
                .contains("c.enabled = 1")
                .contains("o.enabled = 1")
                .contains("COALESCE(a.enabled, o.default_access) = 1");
    }

    @Test
    void migrationOwnsBothCliAndDynamicOfferingConcurrencyConstraints() throws Exception {
        String sql = Files.readString(Path.of(
                "../openflash_user/openflash_back/src/main/resources/db/migration/"
                        + "V59__create_platform_ai_catalog.sql"));
        assertThat(sql)
                .contains("UNIQUE KEY `uk_platform_ai_cli_key` (`cli_key`)")
                .contains("UNIQUE KEY `uk_platform_ai_dynamic_connection` "
                        + "(`dynamic_connection_id`)")
                .contains("`dynamic_connection_id` bigint GENERATED ALWAYS AS")
                .contains(") VIRTUAL")
                .contains("CASE WHEN `model_key` IS NULL THEN `connection_id` ELSE NULL END");
    }

    @Test
    void userOverrideWinsAndGlobalAllowAreResolvedByAuthoritativeUsableQuery() {
        when(offerings.findUsableByUserId(1L)).thenReturn(List.of(apiUsable()));
        when(offerings.findUsableByUserId(2L)).thenReturn(List.of(apiUsable()));

        assertThat(service.listUsableOfferings(1L)).hasSize(1);
        assertThat(service.listUsableOfferings(2L)).hasSize(1);

        verify(offerings).findUsableByUserId(1L);
        verify(offerings).findUsableByUserId(2L);
    }

    @Test
    void unsupportedDatabaseProtocolOrCliRegistrationIsFilteredAndRejected() {
        UsableOfferingRow unsupported = new UsableOfferingRow(
                8L, "forged-cli", null, true, true, 0,
                9L, "forged-connection", "CLI", "OTHER_APP_SERVER", "other",
                null, false, true, 0);
        when(offerings.findUsableByUserId(7L)).thenReturn(List.of(unsupported));
        when(offerings.findUsableByKeyAndUserId("forged-cli", 7L))
                .thenReturn(unsupported);

        assertThat(service.listUsableOfferings(7L)).isEmpty();
        assertRuntimeCode(() -> service.models(7L, "forged-cli"),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> service.generate(
                new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 7L, "forged-cli", "gpt-5.4", "low",
                        "prompt", null, null)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        verifyNoInteractions(codex);
    }

    @Test
    void forgedDeletedOrDisabledOfferingIsRejectedBeforeTransport() {
        PlatformAiTransport transport = mock(PlatformAiTransport.class);
        when(transports.require("ANTHROPIC")).thenReturn(transport);
        when(offerings.findUsableByKeyAndUserId("forged", 9L)).thenReturn(null);

        assertRuntimeCode(
                () -> service.generate(new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 9L, "forged", "gpt-5.4", null,
                        "prompt", null, null)),
                RuntimeErrorCode.NOT_FOUND);
        verify(transport, never()).generate(any());
    }

    @Test
    void fixedApiModelCannotBeForgedAndMissingCredentialsFailsClosed() {
        UsableOfferingRow row = apiUsable();
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 9L)).thenReturn(row);

        assertRuntimeCode(
                () -> service.generate(new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 9L, row.offeringKey(), "forged-model", null,
                        "prompt", null, null)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);

        when(secrets.requirePlaintext(row.connectionId()))
                .thenThrow(new openflash_ai_runtime.common.RuntimeException(
                        RuntimeErrorCode.UNAVAILABLE));
        assertRuntimeCode(
                () -> service.generate(new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 9L, row.offeringKey(), row.modelKey(), null,
                        "prompt", null, null)),
                RuntimeErrorCode.UNAVAILABLE);
        verify(transports, never()).require(any());
    }

    @Test
    void authorizedFixedApiGenerationUsesStoredModelAndRuntimeSecret() {
        UsableOfferingRow row = apiUsable();
        PlatformAiTransport transport = mock(PlatformAiTransport.class);
        UUID requestId = UUID.randomUUID();
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 9L)).thenReturn(row);
        when(secrets.requirePlaintext(row.connectionId())).thenReturn("runtime-secret");
        when(transports.require("ANTHROPIC")).thenReturn(transport);
        when(transport.generate(any(), any())).thenReturn("answer");

        assertThat(service.generate(new PlatformAiCatalogService.GenerationCommand(
                requestId, 9L, row.offeringKey(), "gpt-5.4", null,
                "prompt", "system", 0.2))).isEqualTo("answer");

        verify(transport).generate(eq(new PlatformAiTransport.GenerateCommand(
                requestId, "https://api.example.test", "runtime-secret",
                "gpt-5.4", "prompt", "system", 0.2)), any());
    }

    @Test
    void cliModelAndEffortMustExistInLiveCatalogBeforeGeneration() {
        UsableOfferingRow row = codexUsable();
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 7L)).thenReturn(row);
        CodexModelCatalog.Model model = new CodexModelCatalog.Model(
                "id", "gpt-5.4", "GPT-5.4", "desc", true, "low",
                List.of(new CodexModelCatalog.ReasoningEffort("low", "Low")));
        when(codex.models()).thenReturn(CompletableFuture.completedFuture(
                new CodexModelCatalog.Catalog(List.of(model), model)));

        assertRuntimeCode(
                () -> service.generate(new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 7L, row.offeringKey(), "forged", "low",
                        "prompt", null, null)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        verify(codex, never()).generate(any(), any(), any());
    }

    @Test
    void authorizedCliGenerationUsesLiveValidatedModelAndEffort() {
        UsableOfferingRow row = codexUsable();
        UUID requestId = UUID.randomUUID();
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 7L)).thenReturn(row);
        CodexModelCatalog.Model model = new CodexModelCatalog.Model(
                "id", "gpt-5.4", "GPT-5.4", "desc", true, "low",
                List.of(new CodexModelCatalog.ReasoningEffort("low", "Low")));
        when(codex.models()).thenReturn(CompletableFuture.completedFuture(
                new CodexModelCatalog.Catalog(List.of(model), model)));
        when(codex.generate(any(), any(), any(), any())).thenReturn("answer");

        assertThat(service.generate(new PlatformAiCatalogService.GenerationCommand(
                requestId, 7L, row.offeringKey(), "gpt-5.4", "low",
                "prompt", "system", 0.1))).isEqualTo("answer");

        verify(codex).generate(
                eq(requestId), eq("prompt"),
                eq(new openflash_ai_runtime.dto.GenerationProfile(
                        "gpt-5.4", "system", 0.1, "low")),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelWhileAuthoritativeLookupIsBlockedPreventsAnyLaterApiWork() throws Exception {
        UsableOfferingRow row = apiUsable();
        UUID requestId = UUID.randomUUID();
        CountDownLatch lookupEntered = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 9L))
                .thenAnswer(invocation -> {
                    lookupEntered.countDown();
                    assertThat(releaseLookup.await(2, TimeUnit.SECONDS)).isTrue();
                    return row;
                });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> pending = CompletableFuture.supplyAsync(() ->
                    service.generate(new PlatformAiCatalogService.GenerationCommand(
                            requestId, 9L, row.offeringKey(), row.modelKey(), null,
                            "prompt", null, null)), caller);

            assertThat(lookupEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.cancel(requestId)).isTrue();
            releaseLookup.countDown();

            assertThatThrownBy(pending::join)
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasCauseInstanceOf(openflash_ai_runtime.common.RuntimeException.class);
            verifyNoInteractions(secrets, transports, codex);
            assertThat(service.cancel(requestId)).isFalse();
        } finally {
            releaseLookup.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    void generationInputLimitsAreEnforcedBeforeAnyCatalogLookup() {
        assertRuntimeCode(() -> service.generate(
                new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 7L, "offering", "m".repeat(256), null,
                        "prompt", null, 0.2)), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> service.generate(
                new PlatformAiCatalogService.GenerationCommand(
                        UUID.randomUUID(), 7L, "offering", "model", null,
                        "prompt", null, Double.NaN)), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);

        verifyNoInteractions(offerings, secrets, transports, codex);
    }

    @Test
    void registeredCodexCreationUsesStableKeysAndOneNullModelOffering() {
        PlatformAiCatalogService.ConnectionView created = service.createConnection(
                new PlatformAiCatalogService.CreateConnectionCommand(
                        "CLI", "CODEX_APP_SERVER", "codex", null, 3));

        assertThat(created.connectionKey()).isEqualTo("platform-codex");
        ArgumentCaptor<PlatformAiConnection> connection =
                ArgumentCaptor.forClass(PlatformAiConnection.class);
        ArgumentCaptor<PlatformAiOffering> offering =
                ArgumentCaptor.forClass(PlatformAiOffering.class);
        verify(connections).insert(connection.capture());
        verify(offerings).insert(offering.capture());
        assertThat(connection.getValue().connectionKey()).isEqualTo("platform-codex");
        assertThat(connection.getValue().cliKey()).isEqualTo("codex");
        assertThat(offering.getValue().offeringKey()).isEqualTo("platform-codex-cli");
        assertThat(offering.getValue().modelKey()).isNull();
    }

    @Test
    void databaseUniqueCollisionsRejectDuplicateCliConnectionOrDynamicOffering() {
        doThrow(new DuplicateKeyException("duplicate cli_key"))
                .when(connections).insert(any(PlatformAiConnection.class));
        assertRuntimeCode(() -> service.createConnection(
                new PlatformAiCatalogService.CreateConnectionCommand(
                        "CLI", "CODEX_APP_SERVER", "codex", null, 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);

        org.mockito.Mockito.reset(connections);
        doThrow(new DuplicateKeyException("duplicate dynamic_connection_id"))
                .when(offerings).insert(any(PlatformAiOffering.class));
        assertRuntimeCode(() -> service.createConnection(
                new PlatformAiCatalogService.CreateConnectionCommand(
                        "CLI", "CODEX_APP_SERVER", "codex", null, 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    @Test
    void deletedCodexCanBeRecreatedWithExactlyOneStableDynamicOffering() {
        PlatformAiConnection existing = codexConnection();
        when(connections.findByKey("platform-codex")).thenReturn(existing);

        service.deleteConnection("platform-codex");
        service.createConnection(new PlatformAiCatalogService.CreateConnectionCommand(
                "CLI", "CODEX_APP_SERVER", "codex", null, 0));

        verify(connections).deleteByKey("platform-codex");
        ArgumentCaptor<PlatformAiOffering> offering =
                ArgumentCaptor.forClass(PlatformAiOffering.class);
        verify(offerings).insert(offering.capture());
        assertThat(offering.getAllValues()).singleElement().satisfies(created -> {
            assertThat(created.offeringKey()).isEqualTo("platform-codex-cli");
            assertThat(created.modelKey()).isNull();
        });
    }

    @Test
    void unregisteredCliAndApiWithCliKeyAreRejected() {
        assertRuntimeCode(
                () -> service.createConnection(new PlatformAiCatalogService.CreateConnectionCommand(
                        "CLI", "CODEX_APP_SERVER", "other", null, 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(
                () -> service.createConnection(new PlatformAiCatalogService.CreateConnectionCommand(
                        "API", "ANTHROPIC", "codex", "https://api.example.test", 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        verify(connections, never()).insert(any());
    }

    @Test
    void cliRejectsManualOfferingAndNonNullModelUpdates() {
        PlatformAiConnection connection = codexConnection();
        PlatformAiOffering offering = codexOffering();
        when(connections.findByKey(connection.connectionKey())).thenReturn(connection);
        when(connections.findById(connection.id())).thenReturn(connection);
        when(offerings.findByKey(offering.offeringKey())).thenReturn(offering);

        assertRuntimeCode(() -> service.createOffering(
                connection.connectionKey(),
                new PlatformAiCatalogService.CreateOfferingCommand("gpt-5.4", 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> service.updateOffering(
                offering.offeringKey(),
                new PlatformAiCatalogService.UpdateOfferingCommand("gpt-5.4", true, 0)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        verify(offerings, never()).update(
                offering.offeringKey(), "gpt-5.4", true, 0);
    }

    @Test
    void postCutoverCodexWritesTouchOnlyCatalogAndAccessTables() {
        PlatformAiConnection connection = codexConnection();
        PlatformAiOffering offering = codexOffering();
        when(connections.findByKey(connection.connectionKey())).thenReturn(connection);
        when(offerings.findByKey(offering.offeringKey())).thenReturn(offering);

        service.updateConnection(connection.connectionKey(),
                new PlatformAiCatalogService.UpdateConnectionCommand(null, false, 8));
        verify(connections).update(connection.connectionKey(), null, false, 8);
        verify(offerings).updateEnabledByConnectionId(connection.id(), false);

        service.setDefaultAccess(offering.offeringKey(), true);
        verify(offerings).updateDefaultAccess(offering.offeringKey(), true);

        service.setUserAccess(offering.offeringKey(), 71L, false);
        verify(access).upsert(71L, offering.id(), false);

        service.deleteUserAccess(offering.offeringKey(), 71L);
        verify(access).delete(71L, offering.id());
    }

    @Test
    void apiConnectionAndOfferingEnabledWritesRemainIndependent() {
        PlatformAiConnection connection = apiConnection();
        PlatformAiOffering offering = apiOffering();
        when(connections.findByKey(connection.connectionKey())).thenReturn(connection);
        when(connections.findById(connection.id())).thenReturn(connection);
        when(offerings.findByKey(offering.offeringKey())).thenReturn(offering);

        service.updateConnection(connection.connectionKey(),
                new PlatformAiCatalogService.UpdateConnectionCommand(
                        "https://new.example.test", false, 1));
        service.updateOffering(offering.offeringKey(),
                new PlatformAiCatalogService.UpdateOfferingCommand("gpt-5.4", false, 2));

        verify(connections).update(
                connection.connectionKey(), "https://new.example.test", false, 1);
        verify(offerings, never()).updateEnabledByConnectionId(connection.id(), false);
        verify(offerings).update(offering.offeringKey(), "gpt-5.4", false, 2);
    }

    @Test
    void catalogMutationsRemainSpringTransactions() throws Exception {
        assertThat(PlatformAiCatalogServiceImpl.class
                .getMethod("updateConnection", String.class,
                        PlatformAiCatalogService.UpdateConnectionCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PlatformAiCatalogServiceImpl.class
                .getMethod("setDefaultAccess", String.class, boolean.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PlatformAiCatalogServiceImpl.class
                .getMethod("setUserAccess", String.class, long.class, boolean.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static void assertRuntimeCode(Runnable action, RuntimeErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(code);
    }

    private static PlatformAiConnection codexConnection() {
        return new PlatformAiConnection(
                1L, "platform-codex", "CLI", "CODEX_APP_SERVER", "codex",
                null, false, true, 0);
    }

    private static PlatformAiOffering codexOffering() {
        return new PlatformAiOffering(2L, 1L, "platform-codex-cli", null,
                true, false, 0);
    }

    private static PlatformAiConnection apiConnection() {
        return new PlatformAiConnection(
                3L, "platform-api", "API", "ANTHROPIC", null,
                "https://api.example.test", true, true, 0);
    }

    private static PlatformAiOffering apiOffering() {
        return new PlatformAiOffering(4L, 3L, "platform-api-model", "gpt-5.4",
                true, false, 0);
    }

    private static UsableOfferingRow apiUsable() {
        return new UsableOfferingRow(
                4L, "platform-api-model", "gpt-5.4", true, false, 0,
                3L, "platform-api", "API", "ANTHROPIC", null,
                "https://api.example.test", true, true, 0);
    }

    private static UsableOfferingRow codexUsable() {
        return new UsableOfferingRow(
                2L, "platform-codex-cli", null, true, false, 0,
                1L, "platform-codex", "CLI", "CODEX_APP_SERVER", "codex",
                null, false, true, 0);
    }

}
