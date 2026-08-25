package openflash_core.service;

import openflash_core.config.AiProperties;

/** 合并基础 profile 与用户实际模型选择. */
public interface AiProfileResolver {

    AiProperties.AiProfile applyModel(AiProperties.AiProfile baseProfile, String model);

    String readUserModel(Long userId);

    String resolveUserModelOrNull(Long userId);

    AiProperties.AiProfile applyUserModel(AiProperties.AiProfile baseProfile, Long userId);
}
