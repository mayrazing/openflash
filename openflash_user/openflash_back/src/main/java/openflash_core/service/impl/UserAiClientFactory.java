package openflash_core.service.impl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.LogLevel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.service.UserAiConfigProvider;
import openflash_core.config.AiProperties;
import openflash_core.common.AppException;
import openflash_core.security.OutboundUrlValidator;
import openflash_core.security.OutboundUrlValidator.ResolvedTarget;
import openflash_core.common.AiErrorCode;

/**
 * 通用用户 AI 客户端工厂：只负责按用户配置创建模型客户端并缓存。
 * 具体插件业务通过该底座调用模型，核心不持有插件业务流程。
 */
@Service
public class UserAiClientFactory {

    private final UserAiConfigProvider userAiConfigProvider;
    private final AiProperties aiProperties;
    private final boolean anthropicDebugLog;
    private final OutboundUrlValidator outboundUrlValidator;
    private final ConcurrentHashMap<Long, CachedUserAiSession> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> versions = new ConcurrentHashMap<>();
    private final AtomicLong generationSequence = new AtomicLong();
    private final AtomicLong globalGeneration = new AtomicLong();

    public UserAiClientFactory(
        UserAiConfigProvider userAiConfigProvider,
        AiProperties aiProperties,
        @Value("${app.anthropic.debug-log:false}") boolean anthropicDebugLog,
        OutboundUrlValidator outboundUrlValidator
    ) {
        this.userAiConfigProvider = userAiConfigProvider;
        this.aiProperties = aiProperties;
        this.anthropicDebugLog = anthropicDebugLog;
        this.outboundUrlValidator = outboundUrlValidator;
    }

    /** 获取或创建用户的 AI 会话。 */
    public UserAiSession getOrCreate(Long userId) {
        while (true) {
            GenerationToken token = captureGenerationToken(userId);
            CachedUserAiSession cached = cache.get(userId);
            if (cached != null && cached.matches(token) && isCurrent(token)) {
                return cached.session();
            }
            if (cached != null) cache.remove(userId, cached);

            AiClientConfigDto config = userAiConfigProvider.getDecryptedAiClientConfig(userId);
            if (!isCurrent(token)) continue;

            UserAiSession session = getOrCreate(userId, config, token);
            if (isCurrent(token)) return session;
        }
    }

    /**
     * 为 DB snapshot 前置采样不可伪造的 user/global generation token。
     */
    public GenerationToken captureGenerationToken(Long userId) {
        while (true) {
            long global = globalGeneration.get();
            AtomicLong userGeneration = versionFor(userId);
            long user = userGeneration.accumulateAndGet(global, Math::max);
            if (globalGeneration.get() == global && userGeneration.get() == user) {
                return new GenerationToken(this, userId, user, global);
            }
        }
    }

    /**
     * 按调用方已读取的配置 snapshot 创建 uncached 会话。
     * 无前置 token 无法证明 snapshot 仍 current，因此兼容 overload 禁止写 cache。
     */
    public UserAiSession getOrCreate(Long userId, AiClientConfigDto config) {
        return buildSession(config);
    }

    /**
     * 按前置 token 与同次 DB snapshot 获取会话；token stale 时仅返回 uncached snapshot client。
     */
    public UserAiSession getOrCreate(
        Long userId,
        AiClientConfigDto config,
        GenerationToken token
    ) {
        if (!isCurrentForUser(userId, token)) {
            return buildSession(config);
        }

        CachedUserAiSession cached = cache.get(userId);
        if (cached != null && cached.matches(token) && cached.config().equals(config)
                && isCurrent(token)) {
            return cached.session();
        }

        UserAiSession session = buildSession(config);
        if (!isCurrent(token)) return session;

        CachedUserAiSession created = new CachedUserAiSession(
            config, session, token.userGeneration, token.globalGeneration);
        while (true) {
            if (!isCurrent(token)) return session;

            cached = cache.get(userId);
            if (cached != null && cached.matches(token) && cached.config().equals(config)
                    && isCurrent(token)) {
                return cached.session();
            }

            boolean installed = cached == null
                ? cache.putIfAbsent(userId, created) == null
                : cache.replace(userId, cached, created);
            if (!installed) continue;
            if (isCurrent(token)) return session;
            cache.remove(userId, created);
            return session;
        }
    }

    /** 清除缓存，配置更新后调用。 */
    public void evict(Long userId) {
        versionFor(userId).accumulateAndGet(nextGeneration(), Math::max);
        cache.remove(userId);
    }

    /** 清除全部缓存，并阻止清除前已开始的 build 重新写回。 */
    public void evictAll() {
        globalGeneration.accumulateAndGet(nextGeneration(), Math::max);
        cache.clear();
    }

    private UserAiSession buildSession(AiClientConfigDto config) {
        String provider = config.provider();
        String model = config.model();
        ChatModel chatModel = buildAnthropicModel(config);
        return new UserAiSession(chatModel, provider, model);
    }

    /** 使用用户供应商配置创建 Anthropic 兼容聊天模型。 */
    private ChatModel buildAnthropicModel(AiClientConfigDto config) {
        String baseUrl = config.baseUrl();
        String apiKey = config.apiKey();
        String model = config.model();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        ResolvedTarget target;
        try {
            target = outboundUrlValidator.resolve(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new AppException(AiErrorCode.AI_MODEL_DISCOVERY_INVALID_URL);
        }

        long timeoutMillis = resolveTimeoutMillis();
        Duration timeout = Duration.ofMillis(timeoutMillis);
        LogLevel logLevel = anthropicDebugLog ? LogLevel.DEBUG : LogLevel.OFF;

        PinnedAnthropicClients.ClientPair clients =
                PinnedAnthropicClients.build(target, apiKey, timeout, logLevel);
        AnthropicClient client = clients.sync();
        AnthropicClientAsync asyncClient = clients.async();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .model(model)
            .build();

        return AnthropicChatModel.builder()
            .anthropicClient(client)
            .anthropicClientAsync(asyncClient)
            .options(options)
            .build();
    }

    private AtomicLong versionFor(Long userId) {
        return versions.computeIfAbsent(userId, key -> new AtomicLong(globalGeneration.get()));
    }

    private long nextGeneration() {
        return generationSequence.incrementAndGet();
    }

    private boolean isCurrentForUser(Long userId, GenerationToken token) {
        return token != null && token.issuer == this && Objects.equals(token.userId, userId)
            && isCurrent(token);
    }

    private boolean isCurrent(GenerationToken token) {
        AtomicLong userGeneration = versions.get(token.userId);
        if (userGeneration == null) return false;

        long global = globalGeneration.get();
        long user = userGeneration.get();
        return global == token.globalGeneration
            && user == token.userGeneration
            && globalGeneration.get() == global
            && userGeneration.get() == user;
    }

    /** 解析响应超时，非法值回退 30 秒。 */
    long resolveTimeoutMillis() {
        long t = aiProperties.getTimeoutMillis();
        return t > 0 ? t : 30000L;
    }

    public record UserAiSession(ChatModel chatModel, String provider, String model) {}

    private record CachedUserAiSession(
        AiClientConfigDto config,
        UserAiSession session,
        long userGeneration,
        long globalGeneration
    ) {
        private boolean matches(GenerationToken token) {
            return userGeneration == token.userGeneration
                && globalGeneration == token.globalGeneration;
        }
    }

    /** 仅由当前 factory 实例签发；调用方只能透传，不能构造 generation 值。 */
    public static final class GenerationToken {
        private final UserAiClientFactory issuer;
        private final Long userId;
        private final long userGeneration;
        private final long globalGeneration;

        private GenerationToken(
            UserAiClientFactory issuer,
            Long userId,
            long userGeneration,
            long globalGeneration
        ) {
            this.issuer = issuer;
            this.userId = userId;
            this.userGeneration = userGeneration;
            this.globalGeneration = globalGeneration;
        }
    }

}
