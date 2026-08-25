package openflash_core.service.impl;

import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.OutputConfig;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;
import openflash_core.config.AiProperties;
import openflash_core.service.ProviderOptionsFactory;

/**
 * 生成 Anthropic 兼容模型调用参数，保留页面 profile 的模型和温度。
 */
@Component
public class AnthropicProviderOptionsFactory implements ProviderOptionsFactory {

    /** 显式禁用 thinking，避免 DeepSeek anthropic 端点默认开启 thinking 块污染输出。 */
    private static final ThinkingConfigParam THINKING_DISABLED =
        ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build());

    /**
     * 根据 profile 生成 Anthropic 调用选项。
     * 显式设置 maxTokens 避免 Spring AI 默认 4096 在长输出时被截断；显式 disable thinking 避免
     * DeepSeek 等兼容端点默认返回 thinking 块导致内容格式漂移。
     */
    @Override
    public ChatOptions buildOptions(AiProperties.AiProfile profile) {
        return buildOptions(profile, null);
    }

    /** 把用户保存的 effort 写入 Anthropic output_config. */
    @Override
    public ChatOptions buildOptions(AiProperties.AiProfile profile, String reasoningEffort) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
            .model(profile.getModel())
            .temperature(profile.getTemperature())
            .maxTokens(8192)
            .thinking(THINKING_DISABLED);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.outputConfig(OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(reasoningEffort))
                    .build());
        }
        return builder.build();
    }
}
