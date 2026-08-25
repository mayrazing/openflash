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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformSecretService;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper.UsableOfferingRow;
import openflash_ai_runtime.mapper.PlatformAiUserAccessMapper;
import openflash_ai_runtime.common.RuntimeExceptionHandler;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.impl.PlatformAiCatalogServiceImpl;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;
import openflash_ai_runtime.security.InternalAccessFilter;
import openflash_ai_runtime.security.InternalTokenGuard;
import openflash_ai_runtime.security.GenerationRequestSizeFilter;
import openflash_ai_runtime.transport.PlatformAiTransport;
import openflash_ai_runtime.transport.PlatformAiTransportRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PlatformAiCoreControllerTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String CORE_TOKEN = "core-token";
    private static final UUID REQUEST_ID = UUID.fromString(
            "12345678-1234-4234-9234-123456789abc");
    private PlatformAiCatalogService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PlatformAiCatalogService.class);
        mvc = controllerMvc(service);
    }

    private static MockMvc controllerMvc(PlatformAiCatalogService service) {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getInternal().setAdminToken(ADMIN_TOKEN);
        properties.getInternal().setCoreToken(CORE_TOKEN);
        SafeErrorResponseWriter writer = new SafeErrorResponseWriter(new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(new PlatformAiCoreController(service))
                .setControllerAdvice(new RuntimeExceptionHandler(writer))
                .setMessageConverters(strictJsonConverter())
                .addFilters(
                        new InternalAccessFilter(new InternalTokenGuard(properties), writer),
                        new GenerationRequestSizeFilter(writer))
                .build();
    }

    @Test
    void generationRechecksDatabaseAccessAfterCoreSawOffering() throws Exception {
        AtomicBoolean accessGranted = new AtomicBoolean(true);
        PlatformAiOfferingMapper offerings = mock(PlatformAiOfferingMapper.class);
        UsableOfferingRow row = apiUsable();
        when(offerings.findUsableByUserId(7L)).thenAnswer(
                ignored -> accessGranted.get() ? List.of(row) : List.of());
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 7L)).thenAnswer(
                ignored -> accessGranted.get() ? row : null);
        PlatformGenerationRequestRegistry requestRegistry =
                new PlatformGenerationRequestRegistry();
        TestTransport transport = new TestTransport(requestRegistry, false);
        MockMvc runtimeMvc = runtimeMvc(offerings, transport, requestRegistry);

        runtimeMvc.perform(get("/api/internal/core/platform-ai/offerings")
                        .queryParam("userId", "7")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offeringKey").value(row.offeringKey()))
                .andExpect(jsonPath("$[0].accessGranted").value(true));

        accessGranted.set(false);

        runtimeMvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"requestId":"12345678-1234-4234-9234-123456789abc",
                             "userId":7,"offeringKey":"platform-api-model",
                             "model":"gpt-5.4","reasoningEffort":null,
                             "prompt":"apple","systemPrompt":null,"temperature":null}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        assertThat(transport.generationCount()).hasValue(0);
    }

    @Test
    void deleteCancelsInFlightGenerationAndRegistryDropsFinishedRequest() throws Exception {
        PlatformAiOfferingMapper offerings = mock(PlatformAiOfferingMapper.class);
        UsableOfferingRow row = apiUsable();
        when(offerings.findUsableByKeyAndUserId(row.offeringKey(), 7L)).thenReturn(row);
        PlatformGenerationRequestRegistry requestRegistry =
                new PlatformGenerationRequestRegistry();
        TestTransport transport = new TestTransport(requestRegistry, true);
        MockMvc runtimeMvc = runtimeMvc(offerings, transport, requestRegistry);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> pending = caller.submit(() -> runtimeMvc.perform(
                            post("/api/internal/core/platform-ai/generations")
                                    .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                        {"requestId":"12345678-1234-4234-9234-123456789abc",
                                         "userId":7,"offeringKey":"platform-api-model",
                                         "model":"gpt-5.4","reasoningEffort":null,
                                         "prompt":"apple","systemPrompt":null,
                                         "temperature":null}
                                        """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(50301))
                    .andReturn());

            assertThat(transport.awaitStarted()).isTrue();
            runtimeMvc.perform(delete(
                            "/api/internal/core/platform-ai/generations/{requestId}", REQUEST_ID)
                            .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(true));

            pending.get(2, TimeUnit.SECONDS);
            runtimeMvc.perform(delete(
                            "/api/internal/core/platform-ai/generations/{requestId}", REQUEST_ID)
                            .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(false));
            assertThat(transport.cancellationCount()).hasValue(1);
            var reused = requestRegistry.reserve(REQUEST_ID);
            requestRegistry.complete(reused);
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void exactCoreApiReturnsOnlySafeContractFields() throws Exception {
        when(service.listUsableOfferings(7L)).thenReturn(List.of(
                new PlatformAiCatalogService.OfferingView(
                        "platform-codex-cli", null, true, false, 0,
                        "AVAILABLE", "CLI", "CODEX_APP_SERVER")));
        when(service.models(7L, "platform-codex-cli")).thenReturn(
                new PlatformAiCatalogService.ModelsView("AVAILABLE", List.of(
                        new PlatformAiCatalogService.ModelView(
                                "gpt-5.4", "GPT-5.4", "desc", true, "low",
                                List.of(new PlatformAiCatalogService.ReasoningEffortView(
                                        "low", "Low"))))));
        when(service.generate(any())).thenReturn("answer");
        when(service.cancel(REQUEST_ID)).thenReturn(true);

        String offerings = mvc.perform(get("/api/internal/core/platform-ai/offerings")
                        .queryParam("userId", "7")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offeringKey").value("platform-codex-cli"))
                .andExpect(jsonPath("$[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$[0].accessGranted").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(offerings).doesNotContain(
                "baseUrl", "credentialsConfigured", "apiKey", "secret", "connectionKey");

        mvc.perform(get("/api/internal/core/platform-ai/offerings/platform-codex-cli/models")
                        .queryParam("userId", "7")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.models[0].model").value("gpt-5.4"));

        mvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"requestId":"12345678-1234-4234-9234-123456789abc",
                             "userId":7,"offeringKey":"platform-codex-cli",
                             "model":"gpt-5.4","reasoningEffort":"low",
                             "prompt":"apple","systemPrompt":"safe","temperature":0.2}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("answer"));
        mvc.perform(delete("/api/internal/core/platform-ai/generations/{requestId}", REQUEST_ID)
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
        verify(service).models(7L, "platform-codex-cli");
        verify(service).cancel(REQUEST_ID);
    }

    @Test
    void adminTokenCannotReachCoreAndPayloadAllowlistRejectsUnknownFields() throws Exception {
        mvc.perform(get("/api/internal/core/platform-ai/offerings")
                        .queryParam("userId", "7")
                        .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"requestId":"12345678-1234-4234-9234-123456789abc",
                             "userId":7,"offeringKey":"platform-codex-cli",
                             "model":"gpt-5.4","reasoningEffort":"low",
                             "prompt":"apple","systemPrompt":null,"temperature":null,
                             "apiKey":"must-not-be-accepted"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(service);
    }

    @Test
    void invalidRequiredFieldsAreRejectedBeforeService() throws Exception {
        mvc.perform(get("/api/internal/core/platform-ai/offerings")
                        .queryParam("userId", "0")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(service);
    }

    @Test
    void oversizedAndNonFiniteGenerationFieldsAreRejectedBeforeService() throws Exception {
        String prefix = """
                {"requestId":"12345678-1234-4234-9234-123456789abc",
                 "userId":7,"offeringKey":"platform-codex-cli","model":""";
        String suffix = "\",\"reasoningEffort\":\"low\",\"prompt\":\"apple\"}";
        mvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + "m".repeat(256) + suffix))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/internal/core/platform-ai/generations")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + "gpt-5.4" +
                                "\",\"reasoningEffort\":\"low\",\"prompt\":\"apple\"," +
                                "\"temperature\":\"NaN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(service);
    }

    private static MockMvc runtimeMvc(
            PlatformAiOfferingMapper offerings,
            TestTransport transport,
            PlatformGenerationRequestRegistry requestRegistry) {
        PlatformSecretService secrets = mock(PlatformSecretService.class);
        when(secrets.requirePlaintext(3L)).thenReturn("runtime-secret");
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("platformAiOfferingMapper", offerings);
        beans.registerSingleton("platformSecretService", secrets);
        beans.registerSingleton("platformAiTransportRegistry",
                new PlatformAiTransportRegistry(List.of(transport)));
        PlatformAiCatalogService runtimeService = new PlatformAiCatalogServiceImpl(
                beans.getBeanProvider(PlatformAiConnectionMapper.class),
                beans.getBeanProvider(PlatformAiOfferingMapper.class),
                beans.getBeanProvider(PlatformAiUserAccessMapper.class),
                beans.getBeanProvider(PlatformSecretService.class),
                beans.getBeanProvider(PlatformAiTransportRegistry.class),
                beans.getBeanProvider(CodexRuntimeService.class),
                requestRegistry);
        return controllerMvc(runtimeService);
    }

    private static UsableOfferingRow apiUsable() {
        return new UsableOfferingRow(
                4L, "platform-api-model", "gpt-5.4", true, false, 0,
                3L, "platform-api", "API", "ANTHROPIC", null,
                "https://api.example.test", true, true, 0);
    }

    private static final class TestTransport implements PlatformAiTransport {

        private final PlatformGenerationRequestRegistry requestRegistry;
        private final boolean blocking;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);
        private final AtomicInteger generations = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();

        private TestTransport(
                PlatformGenerationRequestRegistry requestRegistry,
                boolean blocking) {
            this.requestRegistry = requestRegistry;
            this.blocking = blocking;
        }

        @Override
        public String protocol() {
            return "ANTHROPIC";
        }

        @Override
        public List<String> discoverModels(ConnectionTarget target) {
            return List.of("gpt-5.4");
        }

        @Override
        public String generate(GenerateCommand command) {
            throw new AssertionError("service must pass shared request state");
        }

        @Override
        public String generate(GenerateCommand command, RequestState requestState) {
            generations.incrementAndGet();
            if (!blocking) return "answer";
            if (!requestRegistry.bind(requestState, () -> {
                cancellations.incrementAndGet();
                cancelled.countDown();
            })) {
                throw unavailable();
            }
            started.countDown();
            try {
                if (!cancelled.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("generation cancellation timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            throw unavailable();
        }

        @Override
        public boolean cancel(UUID requestId) {
            return false;
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        private AtomicInteger generationCount() {
            return generations;
        }

        private AtomicInteger cancellationCount() {
            return cancellations;
        }

        private static openflash_ai_runtime.common.RuntimeException unavailable() {
            return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
        }
    }

    private static JacksonJsonHttpMessageConverter strictJsonConverter() {
        return new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }
}
