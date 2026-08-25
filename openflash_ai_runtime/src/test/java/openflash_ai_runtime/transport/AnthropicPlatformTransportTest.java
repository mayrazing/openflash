package openflash_ai_runtime.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.security.OutboundUrlValidator;
import org.junit.jupiter.api.Test;

class AnthropicPlatformTransportTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void validatorAllowsOnlyHttpOrHttpsPublicResolvedTargets() throws Exception {
        OutboundUrlValidator validator = new OutboundUrlValidator(
                host -> List.of(InetAddress.getByName("8.8.8.8")));

        assertThat(validator.resolve("https://api.example.test/v1").uri().getScheme())
                .isEqualTo("https");
        assertThat(validator.resolve("http://api.example.test/v1").uri().getScheme())
                .isEqualTo("http");
        assertThatThrownBy(() -> validator.resolve("ftp://api.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.resolve("https://user@api.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.resolve("https://api.example.test/#fragment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.resolve("https://api.example.test:0/v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.resolve("https://api.example.test:65536/v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(validator.resolve("https://api.example.test:65535/v1").uri().getPort())
                .isEqualTo(65535);
    }

    @Test
    void validatorRejectsEveryBlockedOrUnresolvedAddress() throws Exception {
        List<String> blocked = List.of(
                "0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.0.1",
                "192.168.1.1", "169.254.1.1", "224.0.0.1", "::", "::1", "fc00::1");
        for (String address : blocked) {
            OutboundUrlValidator validator = new OutboundUrlValidator(
                    host -> List.of(InetAddress.getByName(address)));
            assertThatThrownBy(() -> validator.resolve("https://api.example.test"))
                    .as(address)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        OutboundUrlValidator empty = new OutboundUrlValidator(host -> List.of());
        assertThatThrownBy(() -> empty.resolve("https://api.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        OutboundUrlValidator mixed = new OutboundUrlValidator(host -> List.of(
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")));
        assertThatThrownBy(() -> mixed.resolve("https://api.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatorRejectsIpv4CompatibleIpv6AndClassifiesMappedIpv4() throws Exception {
        for (String embedded : List.of(
                "127.0.0.1", "10.0.0.1", "169.254.169.254", "8.8.8.8")) {
            OutboundUrlValidator validator = new OutboundUrlValidator(
                    host -> List.of(ipv4Compatible(embedded)));
            assertThatThrownBy(() -> validator.resolve("https://api.example.test"))
                    .as("::" + embedded)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        OutboundUrlValidator mappedPrivate = new OutboundUrlValidator(
                host -> List.of(ipv4Mapped("10.0.0.1")));
        assertThatThrownBy(() -> mappedPrivate.resolve("https://api.example.test"))
                .isInstanceOf(IllegalArgumentException.class);

        OutboundUrlValidator mappedPublic = new OutboundUrlValidator(
                host -> List.of(ipv4Mapped("8.8.8.8")));
        assertThat(mappedPublic.resolve("https://api.example.test").addresses())
                .hasSize(1);

        OutboundUrlValidator globalIpv6 = new OutboundUrlValidator(
                host -> List.of(InetAddress.getByName("2001:4860:4860::8888")));
        assertThat(globalIpv6.resolve("https://api.example.test").addresses())
                .hasSize(1);
    }

    @Test
    void discoveryAndGenerationValidateThenUseOnlyAnthropicFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(
                    "{\"data\":[{\"id\":\"claude-a\"},{\"id\":\"claude-b\"}]}"));
            server.enqueue(new MockResponse().setBody(
                    "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"},"
                            + "{\"type\":\"text\",\"text\":\" world\"}]}"));
            server.start();
            String baseUrl = server.url("/").newBuilder()
                    .host("api.example.test").build().toString();
            AnthropicPlatformTransport transport = transport(server);

            assertThat(transport.discoverModels(
                    new PlatformAiTransport.ConnectionTarget(baseUrl, "secret-key")))
                    .containsExactly("claude-a", "claude-b");
            String result = transport.generate(new PlatformAiTransport.GenerateCommand(
                    UUID.fromString("12345678-1234-4234-9234-123456789abc"),
                    baseUrl, "secret-key", "claude-a", "prompt", "system", 0.25));

            assertThat(result).isEqualTo("hello world");
            var modelsRequest = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(modelsRequest.getPath()).isEqualTo("/v1/models");
            assertThat(modelsRequest.getHeader("x-api-key")).isEqualTo("secret-key");
            var generationRequest = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(generationRequest.getPath()).isEqualTo("/v1/messages");
            assertThat(generationRequest.getHeader("x-api-key")).isEqualTo("secret-key");
            JsonNode body = JSON.readTree(generationRequest.getBody().readUtf8());
            assertThat(body.properties().stream().map(java.util.Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrder(
                            "model", "max_tokens", "messages", "system", "temperature");
            assertThat(body.toString()).doesNotContain(
                    "requestId", "reasoningEffort", "baseUrl", "secret-key");
        }
    }

    @Test
    void cancelTargetsExactlyOneActiveRequestAndFinallyRemovesIt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(
                    okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));
            server.start();
            String baseUrl = server.url("/").newBuilder()
                    .host("api.example.test").build().toString();
            AnthropicPlatformTransport transport = transport(server);
            UUID requestId = UUID.randomUUID();
            CompletableFuture<String> pending = CompletableFuture.supplyAsync(() ->
                    transport.generate(new PlatformAiTransport.GenerateCommand(
                            requestId, baseUrl, "secret-key", "claude-a",
                            "prompt", null, null)));

            assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
            assertThat(transport.cancel(requestId)).isTrue();
            assertThatThrownBy(pending::join)
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasMessageNotContaining("secret-key")
                    .hasMessageNotContaining(baseUrl);
            assertThat(transport.cancel(requestId)).isFalse();
            assertThat(transport.cancel(UUID.randomUUID())).isFalse();
        }
    }

    @Test
    void cancelAfterClientCreationStartsButBeforeCallBindPreventsExecute() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String baseUrl = server.url("/").newBuilder()
                    .host("api.example.test").build().toString();
            OutboundUrlValidator validator = new OutboundUrlValidator(
                    host -> List.of(InetAddress.getByName("8.8.8.8")));
            Dns testDns = host -> host.equals("api.example.test")
                    ? List.of(InetAddress.getByName("127.0.0.1"))
                    : Dns.SYSTEM.lookup(host);
            OkHttpClient client = new OkHttpClient.Builder().dns(testDns).build();
            CountDownLatch factoryEntered = new CountDownLatch(1);
            CountDownLatch releaseFactory = new CountDownLatch(1);
            PlatformGenerationRequestRegistry registry =
                    new PlatformGenerationRequestRegistry();
            AnthropicPlatformTransport transport = new AnthropicPlatformTransport(
                    validator,
                    ignored -> {
                        factoryEntered.countDown();
                        try {
                            if (!releaseFactory.await(2, TimeUnit.SECONDS)) {
                                throw new AssertionError("factory gate timed out");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                        return AnthropicPlatformTransport.ClientLease.borrowed(client);
                    },
                    registry);
            UUID requestId = UUID.randomUUID();
            var state = registry.reserve(requestId);
            CompletableFuture<String> pending = CompletableFuture.supplyAsync(() ->
                    transport.generate(new PlatformAiTransport.GenerateCommand(
                            requestId, baseUrl, "secret-key", "claude-a",
                            "prompt", null, null), state));
            try {
                assertThat(factoryEntered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(registry.cancel(requestId)).isTrue();
                releaseFactory.countDown();

                assertThatThrownBy(pending::join)
                        .isInstanceOf(java.util.concurrent.CompletionException.class)
                        .hasCauseInstanceOf(openflash_ai_runtime.common.RuntimeException.class);
                assertThat(server.getRequestCount()).isZero();
            } finally {
                releaseFactory.countDown();
                registry.complete(state);
                client.dispatcher().executorService().shutdownNow();
                client.connectionPool().evictAll();
            }
        }
    }

    @Test
    void upstreamErrorsAreMappedToSafeRuntimeErrorWithoutBodyOrSecret() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500)
                    .setBody("upstream-secret-body"));
            server.start();
            String baseUrl = server.url("/").newBuilder()
                    .host("api.example.test").build().toString();
            AnthropicPlatformTransport transport = transport(server);

            assertThatThrownBy(() -> transport.generate(
                    new PlatformAiTransport.GenerateCommand(
                            UUID.randomUUID(), baseUrl, "plain-api-secret", "claude-a",
                            "prompt", null, null)))
                    .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                    .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                            .getErrorCode())
                    .isEqualTo(RuntimeErrorCode.UNAVAILABLE);
        }
    }

    @Test
    void transportRejectsOversizeAndNonFinitePayloadBeforeDnsOrClientCreation() {
        OutboundUrlValidator validator = mock(OutboundUrlValidator.class);
        java.util.concurrent.atomic.AtomicInteger clients = new java.util.concurrent.atomic.AtomicInteger();
        AnthropicPlatformTransport transport = new AnthropicPlatformTransport(
                validator, ignored -> {
                    clients.incrementAndGet();
                    return AnthropicPlatformTransport.ClientLease.borrowed(
                            new OkHttpClient());
                });

        assertRuntimeCode(() -> transport.generate(new PlatformAiTransport.GenerateCommand(
                UUID.randomUUID(), "https://api.example.test", "key", "m".repeat(256),
                "prompt", null, 0.2)), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> transport.generate(new PlatformAiTransport.GenerateCommand(
                UUID.randomUUID(), "https://api.example.test", "key", "model",
                "prompt", null, Double.POSITIVE_INFINITY)),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);

        verifyNoInteractions(validator);
        assertThat(clients).hasValue(0);
    }

    @Test
    void sharedTransportRejectsAStateReservedForAnotherRequestBeforeDns() {
        OutboundUrlValidator validator = mock(OutboundUrlValidator.class);
        PlatformGenerationRequestRegistry registry =
                new PlatformGenerationRequestRegistry();
        AnthropicPlatformTransport transport = new AnthropicPlatformTransport(
                validator,
                ignored -> AnthropicPlatformTransport.ClientLease.borrowed(
                        new OkHttpClient()),
                registry);
        var state = registry.reserve(UUID.randomUUID());
        try {
            assertRuntimeCode(() -> transport.generate(
                    new PlatformAiTransport.GenerateCommand(
                            UUID.randomUUID(), "https://api.example.test", "key", "model",
                            "prompt", null, 0.2),
                    state), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
            verifyNoInteractions(validator);
        } finally {
            registry.complete(state);
        }
    }

    @Test
    void malformedUrlAndHeaderValuesMapToSafeInvalidRequestInsteadOfGenericFailure() {
        OutboundUrlValidator validator = new OutboundUrlValidator(
                host -> List.of(java.net.InetAddress.getLoopbackAddress().isLoopbackAddress()
                        ? InetAddress.getByName("8.8.8.8")
                        : InetAddress.getByName("8.8.8.8")));
        AnthropicPlatformTransport transport = new AnthropicPlatformTransport(
                validator, ignored -> AnthropicPlatformTransport.ClientLease.borrowed(
                        new OkHttpClient()));

        assertRuntimeCode(() -> transport.discoverModels(
                new PlatformAiTransport.ConnectionTarget(
                        "https://api.example.test:0", "valid-key")),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> transport.discoverModels(
                new PlatformAiTransport.ConnectionTarget(
                        "https://api.example.test:65536", "valid-key")),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> transport.discoverModels(
                new PlatformAiTransport.ConnectionTarget(
                        "https://api.example.test", "bad\r\nheader-value")),
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        assertRuntimeCode(() -> transport.generate(new PlatformAiTransport.GenerateCommand(
                UUID.randomUUID(), "https://api.example.test", "bad\u0000value", "model",
                "prompt", null, 0.2)), RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    @Test
    void productionClientLeaseHasNoIdleReuseAndClosesEveryOwnedDispatcher() throws Exception {
        OutboundUrlValidator.ResolvedTarget target =
                new OutboundUrlValidator.ResolvedTarget(
                        new java.net.URI("https://api.example.test/v1/messages"),
                        List.of(InetAddress.getByName("8.8.8.8")));
        List<AnthropicPlatformTransport.ClientLease> leases =
                java.util.stream.IntStream.range(0, 12)
                        .mapToObj(ignored -> AnthropicPlatformTransport.productionLease(target))
                        .toList();
        leases.forEach(lease -> {
            assertThat(lease.client().connectionPool().idleConnectionCount()).isZero();
            assertThat(lease.client().dispatcher().executorService().isShutdown()).isFalse();
        });

        ExecutorService closers = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture.allOf(leases.stream()
                    .map(lease -> CompletableFuture.runAsync(lease::close, closers))
                    .toArray(CompletableFuture[]::new)).join();
        } finally {
            closers.shutdownNow();
        }

        leases.forEach(lease -> {
            assertThat(lease.client().connectionPool().connectionCount()).isZero();
            assertThat(lease.client().dispatcher().executorService().isShutdown()).isTrue();
            lease.close();
        });
    }

    @Test
    void borrowedTestClientIsNeverShutdownByTransportCleanup() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"data\":[{\"id\":\"claude-a\"}]}"));
            server.start();
            String baseUrl = server.url("/").newBuilder()
                    .host("api.example.test").build().toString();
            OutboundUrlValidator validator = new OutboundUrlValidator(
                    host -> List.of(InetAddress.getByName("8.8.8.8")));
            Dns testDns = host -> host.equals("api.example.test")
                    ? List.of(InetAddress.getByName("127.0.0.1"))
                    : Dns.SYSTEM.lookup(host);
            OkHttpClient client = new OkHttpClient.Builder().dns(testDns).build();
            AnthropicPlatformTransport transport = new AnthropicPlatformTransport(
                    validator,
                    ignored -> AnthropicPlatformTransport.ClientLease.borrowed(client));
            try {
                assertThat(transport.discoverModels(
                        new PlatformAiTransport.ConnectionTarget(baseUrl, "key")))
                        .containsExactly("claude-a");
                assertThat(client.dispatcher().executorService().isShutdown()).isFalse();
            } finally {
                client.dispatcher().executorService().shutdownNow();
                client.connectionPool().evictAll();
            }
        }
    }

    private static void assertRuntimeCode(Runnable action, RuntimeErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(code);
    }

    private static Inet6Address ipv4Compatible(String embedded) throws Exception {
        byte[] bytes = new byte[16];
        byte[] ipv4 = InetAddress.getByName(embedded).getAddress();
        System.arraycopy(ipv4, 0, bytes, 12, ipv4.length);
        return Inet6Address.getByAddress(null, bytes, -1);
    }

    private static Inet6Address ipv4Mapped(String embedded) throws Exception {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        byte[] ipv4 = InetAddress.getByName(embedded).getAddress();
        System.arraycopy(ipv4, 0, bytes, 12, ipv4.length);
        return Inet6Address.getByAddress(null, bytes, -1);
    }

    private static AnthropicPlatformTransport transport(MockWebServer server) throws Exception {
        OutboundUrlValidator validator = new OutboundUrlValidator(
                host -> List.of(InetAddress.getByName("8.8.8.8")));
        Dns testDns = host -> host.equals("api.example.test")
                ? List.of(InetAddress.getByName("127.0.0.1"))
                : Dns.SYSTEM.lookup(host);
        return new AnthropicPlatformTransport(
                validator, ignored -> AnthropicPlatformTransport.ClientLease.owned(
                        new OkHttpClient.Builder()
                                .dns(testDns)
                                .proxy(Proxy.NO_PROXY)
                                .callTimeout(Duration.ofSeconds(2))
                                .build()));
    }
}
