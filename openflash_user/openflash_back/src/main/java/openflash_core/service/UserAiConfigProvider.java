package openflash_core.service;

import openflash_core.dto.AiClientConfigDto;

/** 向 AI 客户端工厂提供当前用户的解密连接快照. */
public interface UserAiConfigProvider {

    AiClientConfigDto getDecryptedAiClientConfig(Long userId);
}
