package openflash_core.service;

import openflash_core.config.AiProperties;
import openflash_core.dto.ActiveAiSelectionDto;

/** 插件调用用户当前 AI provider 的稳定入口. */
public interface AiGateway {

    String chat(String prompt, AiProperties.AiProfile profile, Long userId);

    /**
     * 用一次解析出的 active selection 同时执行调用前校验和实际路由.
     */
    String chat(
            String prompt,
            AiProperties.AiProfile profile,
            Long userId,
            AiDispatchValidator validator);

    @FunctionalInterface
    interface AiDispatchValidator {
        void validate(
                ActiveAiSelectionDto activeSelection,
                AiProperties.AiProfile effectiveProfile);
    }
}
