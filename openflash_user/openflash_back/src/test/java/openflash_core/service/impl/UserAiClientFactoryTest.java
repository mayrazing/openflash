package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.security.OutboundUrlValidator;
import openflash_core.common.AiErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.service.UserAiConfigProvider;

class UserAiClientFactoryTest {

    /** 未配置时工厂原样抛出 AI_NOT_CONFIGURED。 */
    @Test
    void getOrCreateThrowsWhenUserNotConfigured() {
        Fixture f = new Fixture();
        f.svc.exception = new AppException(AiErrorCode.AI_NOT_CONFIGURED);

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
        assertEquals(11L, f.svc.lastUserId);
    }

    /** 第二次调用直接复用已缓存的 ChatModel。 */
    @Test
    void getOrCreateReturnsCachedClientOnSecondCall() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "https://api.deepseek.com/anthropic", "sk-x", "deepseek-chat");

        UserAiClientFactory.UserAiSession first  = f.factory.getOrCreate(11L);
        UserAiClientFactory.UserAiSession second = f.factory.getOrCreate(11L);

        assertSame(first.chatModel(), second.chatModel());
        assertEquals("deepseek", first.provider());
        assertEquals("deepseek-chat", first.model());
        assertEquals(1, f.svc.calls);
    }

    /** evict 后重新创建新会话。 */
    @Test
    void evictThenGetOrCreateCreatesNewClient() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "https://api.deepseek.com/anthropic", "sk-x", "deepseek-chat");

        UserAiClientFactory.UserAiSession first = f.factory.getOrCreate(11L);
        f.factory.evict(11L);
        UserAiClientFactory.UserAiSession second = f.factory.getOrCreate(11L);

        assertNotSame(first.chatModel(), second.chatModel());
        assertEquals(2, f.svc.calls);
    }

    /** entry 前 evict 使旧 token 失效；旧 snapshot 仅返回给 caller，不能进入 cache。 */
    @Test
    void evictBeforeExplicitEntryReturnsSnapshotOnlyAndCachesCurrentConfig() {
        Fixture f = new Fixture();
        AiClientConfigDto oldConfig = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");
        UserAiClientFactory.GenerationToken oldToken =
            f.factory.captureGenerationToken(11L);

        f.svc.config = anthropicConfig(
            "new-provider", "https://new.example.com", "sk-new", "new-model");
        f.factory.evict(11L);

        UserAiClientFactory.UserAiSession explicit =
            f.factory.getOrCreate(11L, oldConfig, oldToken);
        UserAiClientFactory.UserAiSession current = f.factory.getOrCreate(11L);
        UserAiClientFactory.UserAiSession cachedCurrent = f.factory.getOrCreate(11L);

        assertEquals("old-model", explicit.model());
        assertEquals("new-model", current.model());
        assertNotSame(explicit.chatModel(), current.chatModel());
        assertSame(current.chatModel(), cachedCurrent.chatModel());
        assertEquals(1, f.svc.calls);
    }

    /** entry 前 evictAll 使旧 global token 失效；旧 snapshot 不能进入 cache。 */
    @Test
    void evictAllBeforeExplicitEntryReturnsSnapshotOnlyAndCachesCurrentConfig() {
        Fixture f = new Fixture();
        AiClientConfigDto oldConfig = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");
        UserAiClientFactory.GenerationToken oldToken =
            f.factory.captureGenerationToken(11L);

        f.svc.config = anthropicConfig(
            "new-provider", "https://new.example.com", "sk-new", "new-model");
        f.factory.evictAll();

        UserAiClientFactory.UserAiSession explicit =
            f.factory.getOrCreate(11L, oldConfig, oldToken);
        UserAiClientFactory.UserAiSession current = f.factory.getOrCreate(11L);
        UserAiClientFactory.UserAiSession cachedCurrent = f.factory.getOrCreate(11L);

        assertEquals("old-model", explicit.model());
        assertEquals("new-model", current.model());
        assertNotSame(explicit.chatModel(), current.chatModel());
        assertSame(current.chatModel(), cachedCurrent.chatModel());
        assertEquals(1, f.svc.calls);
    }

    /** 两参数兼容 API 无前置 token，必须始终返回 uncached snapshot client。 */
    @Test
    void twoArgumentSnapshotOverloadCannotCachePreEntryStaleConfig() {
        Fixture f = new Fixture();
        AiClientConfigDto oldConfig = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");

        f.svc.config = anthropicConfig(
            "new-provider", "https://new.example.com", "sk-new", "new-model");
        f.factory.evict(11L);

        UserAiClientFactory.UserAiSession explicit = f.factory.getOrCreate(11L, oldConfig);
        UserAiClientFactory.UserAiSession current = f.factory.getOrCreate(11L);
        UserAiClientFactory.UserAiSession cachedCurrent = f.factory.getOrCreate(11L);

        assertEquals("old-model", explicit.model());
        assertEquals("new-model", current.model());
        assertNotSame(explicit.chatModel(), current.chatModel());
        assertSame(current.chatModel(), cachedCurrent.chatModel());
        assertEquals(1, f.svc.calls);
    }

    /** evict 后 explicit caller 仍得到旧 snapshot client，但旧 client 不能复活 cache。 */
    @Test
    void evictDuringExplicitBuildReturnsSnapshotOnlyToCallerAndCachesCurrentConfig() throws Exception {
        BlockingFirstValidator validator = new BlockingFirstValidator();
        Fixture f = new Fixture(30000L, validator);
        AiClientConfigDto oldConfig = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");
        f.svc.config = oldConfig;
        UserAiClientFactory.GenerationToken oldToken =
            f.factory.captureGenerationToken(11L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<UserAiClientFactory.UserAiSession> inFlight =
                executor.submit(() -> f.factory.getOrCreate(11L, oldConfig, oldToken));
            assertTrue(validator.firstCallStarted.await(5, TimeUnit.SECONDS));

            f.svc.config = anthropicConfig(
                "new-provider", "https://new.example.com", "sk-new", "new-model");
            f.factory.evict(11L);
            validator.allowFirstCall.countDown();

            UserAiClientFactory.UserAiSession completed = inFlight.get(5, TimeUnit.SECONDS);
            UserAiClientFactory.UserAiSession current = f.factory.getOrCreate(11L);
            UserAiClientFactory.UserAiSession cachedCurrent = f.factory.getOrCreate(11L);
            assertEquals("old-model", completed.model());
            assertEquals("new-model", current.model());
            assertNotSame(completed.chatModel(), current.chatModel());
            assertSame(current.chatModel(), cachedCurrent.chatModel());
            assertEquals(1, f.svc.calls);
        } finally {
            validator.allowFirstCall.countDown();
            executor.shutdownNow();
        }
    }

    /** evictAll 后 explicit caller 仍得到旧 snapshot client，但旧 client 不能复活 cache。 */
    @Test
    void evictAllDuringExplicitBuildReturnsSnapshotOnlyToCallerAndCachesCurrentConfig() throws Exception {
        BlockingFirstValidator validator = new BlockingFirstValidator();
        Fixture f = new Fixture(30000L, validator);
        AiClientConfigDto oldConfig = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");
        f.svc.config = oldConfig;
        UserAiClientFactory.GenerationToken oldToken =
            f.factory.captureGenerationToken(11L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<UserAiClientFactory.UserAiSession> inFlight =
                executor.submit(() -> f.factory.getOrCreate(11L, oldConfig, oldToken));
            assertTrue(validator.firstCallStarted.await(5, TimeUnit.SECONDS));

            f.svc.config = anthropicConfig(
                "new-provider", "https://new.example.com", "sk-new", "new-model");
            f.factory.evictAll();
            validator.allowFirstCall.countDown();

            UserAiClientFactory.UserAiSession completed = inFlight.get(5, TimeUnit.SECONDS);
            UserAiClientFactory.UserAiSession current = f.factory.getOrCreate(11L);
            UserAiClientFactory.UserAiSession cachedCurrent = f.factory.getOrCreate(11L);
            assertEquals("old-model", completed.model());
            assertEquals("new-model", current.model());
            assertNotSame(completed.chatModel(), current.chatModel());
            assertSame(current.chatModel(), cachedCurrent.chatModel());
            assertEquals(1, f.svc.calls);
        } finally {
            validator.allowFirstCall.countDown();
            executor.shutdownNow();
        }
    }

    /** 显式 snapshot 必须直接用于建 client，不能再次读取可能已切换的 active provider。 */
    @Test
    void getOrCreateWithSnapshotNeverReadsProviderAgain() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig(
            "old-provider", "https://old.example.com", "sk-old", "old-model");
        UserAiClientFactory.UserAiSession oldSession = f.factory.getOrCreate(11L);
        AiClientConfigDto selectedSnapshot = anthropicConfig(
            "selected-provider", "https://selected.example.com", "sk-selected", "selected-model");

        UserAiClientFactory.UserAiSession selectedSession =
            f.factory.getOrCreate(11L, selectedSnapshot);

        assertNotSame(oldSession.chatModel(), selectedSession.chatModel());
        assertEquals("selected-provider", selectedSession.provider());
        assertEquals("selected-model", selectedSession.model());
        assertEquals(1, f.svc.calls);
    }

    /** 会话只暴露 provider 和 model。 */
    @Test
    void sessionExposesProviderAndModelOnly() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("anthropic", "https://api.anthropic.com", "sk-x", "claude-sonnet-4-5");

        UserAiClientFactory.UserAiSession session = f.factory.getOrCreate(11L);
        assertEquals("anthropic", session.provider());
        assertEquals("claude-sonnet-4-5", session.model());
    }

    /** baseUrl 缺失时抛 AI_NOT_CONFIGURED。 */
    @Test
    void getOrCreateThrowsWhenBaseUrlMissing() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "  ", "sk-x", "m");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
    }

    /** apiKey 缺失时抛 AI_NOT_CONFIGURED。 */
    @Test
    void getOrCreateThrowsWhenApiKeyMissing() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "https://api.deepseek.com/anthropic", " ", "m");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
    }

    /** model 缺失时抛 AI_NOT_CONFIGURED。 */
    @Test
    void getOrCreateThrowsWhenModelMissing() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "https://api.deepseek.com/anthropic", "sk-x", "");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
    }

    /** 读取配置的 timeout-millis。 */
    @Test
    void resolveTimeoutMillisUsesConfiguredValue() {
        assertEquals(45000L, new Fixture(45000L).factory.resolveTimeoutMillis());
    }

    /** timeout-millis 非法时回退 30 秒。 */
    @Test
    void resolveTimeoutMillisFallsBackToDefaultWhenInvalid() {
        assertEquals(30000L, new Fixture(0L).factory.resolveTimeoutMillis());
    }

    /** 公网 https baseUrl 创建前校验通过，正常构建。 */
    @Test
    void buildAnthropicModelAcceptsPublicHttps() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("anthropic", "https://api.anthropic.com", "sk-x", "claude-sonnet-4-5");

        UserAiClientFactory.UserAiSession session = f.factory.getOrCreate(11L);
        assertNotNull(session.chatModel());
    }

    /** http baseUrl 在 SDK 创建前被 validator 拒绝，转 AI_MODEL_DISCOVERY_INVALID_URL。 */
    @Test
    void buildAnthropicModelRejectsHttpBaseUrl() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "http://api.example.com", "sk-x", "m");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL, ex.getErrorCode());
    }

    /** localhost 在 SDK 创建前被 validator 拒绝，转 AI_MODEL_DISCOVERY_INVALID_URL。 */
    @Test
    void buildAnthropicModelRejectsLocalhostBaseUrl() {
        Fixture f = new Fixture();
        f.svc.config = anthropicConfig("deepseek", "https://localhost", "sk-x", "m");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL, ex.getErrorCode());
    }

    /** validator 解析到私网 10.x 时转 AI_MODEL_DISCOVERY_INVALID_URL。 */
    @Test
    void buildAnthropicModelRejectsPrivateAddress() {
        Fixture f = new Fixture(30000L, new RejectingValidator());
        f.svc.config = anthropicConfig("deepseek", "https://internal.corp", "sk-x", "m");

        AppException ex = assertThrows(AppException.class, () -> f.factory.getOrCreate(11L));
        assertEquals(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL, ex.getErrorCode());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static AiClientConfigDto anthropicConfig(String provider, String baseUrl, String apiKey, String model) {
        return new AiClientConfigDto(provider, baseUrl, model, apiKey);
    }

    private static final class Fixture {
        final FakeUserAiConfigProvider svc = new FakeUserAiConfigProvider();
        final UserAiClientFactory factory;

        Fixture() { this(30000L, new StubValidator()); }
        Fixture(long timeoutMillis) { this(timeoutMillis, new StubValidator()); }
        Fixture(long timeoutMillis, OutboundUrlValidator validator) {
            AiProperties props = new AiProperties();
            props.setTimeoutMillis(timeoutMillis);
            factory = new UserAiClientFactory(svc, props, false, validator);
        }
    }

    /** 假 validator：所有 https 公网 URL 通过；localhost/loopback 拒绝；非 https 拒绝。 */
    private static class StubValidator extends OutboundUrlValidator {
        @Override
        public OutboundUrlValidator.ResolvedTarget resolve(String rawUrl) {
            try {
                URI uri = new URI(rawUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException("invalid outbound URL");
                }
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (host.equalsIgnoreCase("localhost") || host.startsWith("127.")) {
                    throw new IllegalArgumentException("blocked outbound address");
                }
                return new OutboundUrlValidator.ResolvedTarget(
                        uri, List.of(InetAddress.getByName("8.8.8.8")));
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /** 只阻塞第一次 client build，精确控制 eviction 与 build 的交错。 */
    private static final class BlockingFirstValidator extends StubValidator {
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final CountDownLatch firstCallStarted = new CountDownLatch(1);
        private final CountDownLatch allowFirstCall = new CountDownLatch(1);

        @Override
        public OutboundUrlValidator.ResolvedTarget resolve(String rawUrl) {
            if (first.compareAndSet(true, false)) {
                firstCallStarted.countDown();
                try {
                    if (!allowFirstCall.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to finish first client build");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("client build interrupted", ex);
                }
            }
            return super.resolve(rawUrl);
        }
    }

    /** 假 validator：模拟解析到 10.x 私网地址，全部拒绝。 */
    private static final class RejectingValidator extends OutboundUrlValidator {
        @Override
        public OutboundUrlValidator.ResolvedTarget resolve(String rawUrl) {
            throw new IllegalArgumentException("blocked outbound address");
        }
    }

    private static final class FakeUserAiConfigProvider implements UserAiConfigProvider {
        AiClientConfigDto config;
        RuntimeException exception;
        Long lastUserId;
        int calls;

        @Override
        public AiClientConfigDto getDecryptedAiClientConfig(Long userId) {
            lastUserId = userId;
            calls++;
            if (exception != null) throw exception;
            return config;
        }
    }
}
