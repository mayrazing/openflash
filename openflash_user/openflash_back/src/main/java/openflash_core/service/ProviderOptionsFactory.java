package openflash_core.service;

import org.springframework.ai.chat.prompt.ChatOptions;
import openflash_core.config.AiProperties;

/**
 * 为当前 AI provider 生成模型调用参数，隔离不同 provider 的专属参数差异。
 */
public interface ProviderOptionsFactory {

    /**
     * 根据页面功能使用的 AI profile 生成模型调用参数。
     */
    ChatOptions buildOptions(AiProperties.AiProfile profile);

    /** 根据当前用户已保存的 Anthropic effort 生成调用参数. */
    default ChatOptions buildOptions(
            AiProperties.AiProfile profile, String reasoningEffort) {
        return buildOptions(profile);
    }
}
