package openflash_core.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import openflash_core.config.AiProperties;

/**
 * 验证 Anthropic 兼容选项工厂只把页面 profile 的模型和温度透传给模型调用。
 */
class AnthropicProviderOptionsFactoryTest {

    /**
     * profile 的 model 与 temperature 应原样写入 ChatOptions。
     */
    @Test
    void buildOptionsUsesProfileModelAndTemperature() {
        AnthropicProviderOptionsFactory factory = new AnthropicProviderOptionsFactory();
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setModel("claude-sonnet-4-5");
        profile.setTemperature(0.2);

        ChatOptions options = factory.buildOptions(profile);

        assertThat(options.getModel()).isEqualTo("claude-sonnet-4-5");
        assertThat(options.getTemperature()).isEqualTo(0.2);
    }

    @Test
    void buildOptionsWritesSavedEffortIntoAnthropicOutputConfig() {
        AnthropicProviderOptionsFactory factory = new AnthropicProviderOptionsFactory();
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setModel("claude-opus-4-6");

        AnthropicChatOptions options = (AnthropicChatOptions) factory.buildOptions(profile, "high");

        assertThat(options.getOutputConfig().effort().orElseThrow().asString()).isEqualTo("high");
    }
}
