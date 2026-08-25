package openflash_core.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import openflash_core.service.AiProfileResolver;
import openflash_core.common.AppException;
import openflash_core.common.AppLog;
import openflash_core.config.AiProperties;
import openflash_core.common.AiErrorCode;
import openflash_core.service.UserAiConfigService;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;

/**
 * effective AI profile 单一出口：把「该用户真实模型」合并进基础 profile。
 * 收口原先散落在 AiChatGateway.overrideModel 与 CardSideCompletionTaskProducer 的重复逻辑。
 */
@Service
public class EffectiveAiProfileResolver implements AiProfileResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(EffectiveAiProfileResolver.class);

    private final UserAiConfigService userAiConfigService;
    private final UnifiedAiSelectionServiceImpl selectionService;

    @org.springframework.beans.factory.annotation.Autowired
    public EffectiveAiProfileResolver(
            UserAiConfigService userAiConfigService,
            UnifiedAiSelectionServiceImpl selectionService) {
        this.userAiConfigService = userAiConfigService;
        this.selectionService = selectionService;
    }

    /** 兼容只验证纯 profile 合并的旧单测. */
    public EffectiveAiProfileResolver(UserAiConfigService userAiConfigService) {
        this.userAiConfigService = userAiConfigService;
        this.selectionService = null;
    }

    /**
     * 纯合并：复制 baseProfile（保留 name/system/temperature），用 model 覆盖；
     * model 空白时回退 baseProfile 自带 model。baseProfile 为 null 时返回 null。
     */
    @Override
    public AiProperties.AiProfile applyModel(AiProperties.AiProfile baseProfile, String model) {
        if (baseProfile == null) {
            return null;
        }
        AiProperties.AiProfile copy = new AiProperties.AiProfile();
        copy.setName(baseProfile.getName());
        copy.setSystem(baseProfile.getSystem());
        copy.setTemperature(baseProfile.getTemperature());
        copy.setModel(StringUtils.hasText(model) ? model : baseProfile.getModel());
        return copy;
    }

    /**
     * 读该用户配置里的真实模型值（一次 DB 读 + JSON 解析）。
     * 用户未配置 AI 时抛 AI_NOT_CONFIGURED，不吞异常，容错策略由调用方决定。
     */
    @Override
    public String readUserModel(Long userId) {
        if (selectionService != null) {
            return selectionService.requireActive(userId).model();
        }
        return userAiConfigService.getDecryptedConfig(userId).getConfigValue("model");
    }

    /**
     * 解析当前可用选择的完整缓存身份, 并把选择模型合并进 prompt profile.
     * 选择缺失或失效时保留 UnifiedAiSelectionServiceImpl.requireActive 的错误语义.
     */
    public ActiveAiIdentity requireActiveIdentity(
            Long userId, AiProperties.AiProfile promptProfile) {
        ActiveAiSelectionDto selection = selectionService.requireActive(userId);
        return toActiveIdentity(userId, selection, applyModel(promptProfile, selection.model()));
    }

    /**
     * 将已解析 selection 与实际 dispatch profile 转成统一缓存身份.
     */
    public ActiveAiIdentity toActiveIdentity(
            Long userId,
            ActiveAiSelectionDto selection,
            AiProperties.AiProfile effectivePromptProfile) {
        String selectionKey = selection.source() == AiSource.USER
                ? selection.userProviderKey()
                : selection.offeringKey();
        return new ActiveAiIdentity(
                userId,
                selection.source(),
                selectionKey,
                selection.model(),
                selection.reasoningEffort(),
                selection.providerInstanceIdentity(),
                effectivePromptProfile);
    }

    /**
     * 容错解析该用户当前真实模型：userId 为 null / 用户未配置 AI / 读取失败 时返回 null，
     * 调用方据此回退全局模型，不阻断。批量任务可批级调用一次后整批复用，避免每卡面重复读库。
     * 未配置 AI（AppException）是预期路径安静回退；其余意外异常（如 DB 故障）warn 一条再回退，
     * 避免瞬态故障悄悄回退全局模型却无任何痕迹。
     */
    @Override
    public String resolveUserModelOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return readUserModel(userId);
        } catch (AppException ex) {
            return null;
        } catch (RuntimeException ex) {
            AppLog.warn(LOGGER, AiErrorCode.AI_PROFILE_UNAVAILABLE, "读取用户模型失败，回退全局模型", ex);
            return null;
        }
    }

    /**
     * 解析该用户当前真实模型并合并进 baseProfile。
     * userId 为 null / 用户未配置 AI（getDecryptedConfig 抛异常）/ 模型值为空 时回退全局模型，不阻断。
     * 与 gateway 经 UserAiClientFactory 用的 session.model() 同源（同一份 user config 的
     * model）。
     */
    @Override
    public AiProperties.AiProfile applyUserModel(AiProperties.AiProfile baseProfile, Long userId) {
        return applyModel(baseProfile, resolveUserModelOrNull(userId));
    }

    /** 当前 active AI 与本次 prompt profile 共同组成的缓存身份. */
    public record ActiveAiIdentity(
            Long ownerUserId,
            AiSource source,
            String selectionKey,
            String model,
            String reasoningEffort,
            String providerInstanceIdentity,
            AiProperties.AiProfile effectivePromptProfile) {

        public ActiveAiIdentity(
                AiSource source,
                String selectionKey,
                String model,
                String reasoningEffort,
                AiProperties.AiProfile effectivePromptProfile) {
            this(null, source, selectionKey, model, reasoningEffort, null, effectivePromptProfile);
        }
    }
}
