package openflash_core.service.impl;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import openflash_core.service.AiGateway;
import openflash_core.common.AppException;
import openflash_core.common.AppLog;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.service.AiProfileResolver;
import openflash_core.common.AiErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.service.ProviderOptionsFactory;
import openflash_core.service.UserAiConfigService;
import openflash_core.entity.UserAiConfig;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.client.AiRuntimeCoreClient;

/**
 * 通用 AI 调用底座：只负责把 prompt 发给当前用户配置的模型。
 * 这里不包含 ai-card 插件的卡片解释、缓存、提示词或补全另一面业务。
 */
@Service
public class AiChatGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiChatGateway.class);

    private final UserAiClientFactory userAiClientFactory;
    private final ProviderOptionsFactory providerOptionsFactory;
    private final AiProfileResolver effectiveAiProfileResolver;
    private final UserAiConfigService userAiConfigService;
    private final AiRuntimeCoreClient runtimeClient;
    private final UnifiedAiSelectionServiceImpl selectionService;

    /** 创建生产 AI 聊天入口，协议路由和 transport 共用同一 active config snapshot。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AiChatGateway(
            UserAiClientFactory userAiClientFactory,
            ProviderOptionsFactory providerOptionsFactory,
            AiProfileResolver effectiveAiProfileResolver,
            UserAiConfigService userAiConfigService,
            AiRuntimeCoreClient runtimeClient,
            UnifiedAiSelectionServiceImpl selectionService) {
        this.userAiClientFactory = userAiClientFactory;
        this.providerOptionsFactory = providerOptionsFactory;
        this.effectiveAiProfileResolver = effectiveAiProfileResolver;
        this.userAiConfigService = userAiConfigService;
        this.runtimeClient = runtimeClient;
        this.selectionService = selectionService;
    }

    /**
     * 使用指定用户的 AI 会话发送提示词，并把模型生成的文字返回给调用方。
     */
    @Override
    public String chat(String prompt, AiProperties.AiProfile profile, Long userId) {
        return chat(prompt, profile, userId, (activeSelection, effectiveProfile) -> {
        });
    }

    /**
     * 解析一次 active selection, 先让调用方校验该 snapshot, 再用同一 snapshot 路由.
     */
    @Override
    public String chat(
            String prompt,
            AiProperties.AiProfile profile,
            Long userId,
            AiDispatchValidator validator) {
        if (profile == null) {
            throw new AppException(AiErrorCode.AI_PROFILE_NOT_CONFIGURED);
        }
        if (userId == null) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }

        try {
            ActiveAiSelectionDto active = selectionService.requireActive(userId);
            AiProperties.AiProfile effectiveProfile = effectiveAiProfileResolver.applyModel(profile, active.model());
            validator.validate(active, effectiveProfile);
            return switch (active.source()) {
                case USER -> chatWithUserProvider(
                        prompt, effectiveProfile, userId, active);
                case PLATFORM -> requireContent(runtimeClient.generate(
                        userId, active, prompt, effectiveProfile));
            };
        } catch (AppException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            AppLog.warn(log, AiErrorCode.AI_CONNECTION_FAILED, "AI 连接失败", ex);
            throw new AppException(AiErrorCode.AI_CONNECTION_FAILED);
        } catch (RuntimeException ex) {
            AppLog.warn(log, AiErrorCode.AI_UPSTREAM_UNAVAILABLE, "AI 调用不可用", ex);
            throw new AppException(AiErrorCode.AI_UPSTREAM_UNAVAILABLE);
        }
    }

    private String chatWithUserProvider(
            String prompt,
            AiProperties.AiProfile profile,
            Long userId,
            ActiveAiSelectionDto active) {
        UserAiClientFactory.GenerationToken generationToken = userAiClientFactory.captureGenerationToken(userId);
        UserAiConfig activeConfig = userAiConfigService.getDecryptedConfig(userId);
        if (!Objects.equals(active.userProviderKey(), activeConfig.getProvider())
                || !UserAiConfigService.PROTOCOL_ANTHROPIC.equals(active.protocol())
                || !Objects.equals(active.protocol(), activeConfig.getConfigValue("protocol"))
                || !Objects.equals(active.model(), activeConfig.getConfigValue("model"))
                || !Objects.equals(active.reasoningEffort(),
                        activeConfig.getConfigValue("reasoningEffort"))) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        AiClientConfigDto clientConfig = new AiClientConfigDto(
                active.userProviderKey(),
                activeConfig.getConfigValue("baseUrl"),
                active.model(),
                activeConfig.getConfigValue("apiKey"),
                active.reasoningEffort());
        UserAiClientFactory.UserAiSession session = userAiClientFactory.getOrCreate(
                userId, clientConfig, generationToken);
        if (!Objects.equals(active.userProviderKey(), session.provider())
                || !Objects.equals(active.model(), session.model())) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        ChatResponse response = session.chatModel().call(new Prompt(
                buildMessages(prompt, profile),
                providerOptionsFactory.buildOptions(profile, active.reasoningEffort())));
        return requireContent(extractContent(response));
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new AppException(AiErrorCode.AI_EMPTY_RESPONSE);
        }
        return content;
    }

    /**
     * 组装模型指令；没有 system 提示词时只发送用户输入，避免页面点击后传入空系统指令。
     */
    private List<Message> buildMessages(
            String prompt,
            AiProperties.AiProfile profile) {
        String system = profile.getSystem();
        if (system == null || system.isBlank()) {
            return List.of(new UserMessage(prompt));
        }
        return List.of(new SystemMessage(system), new UserMessage(prompt));
    }

    /**
     * 从模型响应中取出文字；缺少结果时按空响应处理。
     */
    private String extractContent(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getResults())
                || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AppException(AiErrorCode.AI_EMPTY_RESPONSE);
        }
        return response.getResult().getOutput().getText();
    }

}
