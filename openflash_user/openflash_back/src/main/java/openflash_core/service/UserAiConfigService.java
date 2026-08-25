package openflash_core.service;

import java.util.List;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.entity.UserAiConfig;

public interface UserAiConfigService extends UserAiConfigProvider {
    String CODEX_PROVIDER_KEY = "codex-cli";
    String PROTOCOL_ANTHROPIC = "ANTHROPIC";
    String PROTOCOL_CODEX_APP_SERVER = "CODEX_APP_SERVER";

    /** 读取当前用户全部 AI 供应商；返回值不包含明文 Key。 */
    List<UserAiConfig> listProviders(Long userId);

    /** 按用户和内部 provider key 读取一行配置。 */
    UserAiConfig findProvider(Long userId, String providerKey);

    /** 读取当前用户当前启用的 AI 供应商，返回值包含解密后的调用配置。 */
    UserAiConfig getDecryptedConfig(Long userId);

    /** 新增或更新一个 Anthropic 兼容供应商。 */
    void saveProvider(Long userId, String providerKey, String displayName, String website,
                      String note, String baseUrl, String apiKeyPlain, String model,
                      String reasoningEffort);

    default void saveProvider(Long userId, String providerKey, String displayName, String website,
                              String note, String baseUrl, String apiKeyPlain, String model) {
        saveProvider(userId, providerKey, displayName, website, note, baseUrl, apiKeyPlain, model, null);
    }

    /** 新增一个 Anthropic 兼容供应商，内部 provider key 由配置名称生成。 */
    void createProvider(Long userId, String displayName, String website,
                        String note, String baseUrl, String apiKeyPlain, String model,
                        String reasoningEffort);

    default void createProvider(Long userId, String displayName, String website,
                                String note, String baseUrl, String apiKeyPlain, String model) {
        createProvider(userId, displayName, website, note, baseUrl, apiKeyPlain, model, null);
    }

    /** 删除用户自己的一个供应商；允许删除当前启用项，删除后用户可处于未配置 AI 状态。 */
    void deleteProvider(Long userId, String providerKey);

    /** 解析模型发现专用 API Key：新 Key 优先；为空时仅解密当前用户指定 provider 已保存的 Key。
     *  严禁跨用户读取，不依赖当前 active provider，编辑未启用 provider 时也可发现模型。 */
    String resolveDiscoveryApiKey(Long userId, String providerKey, String apiKeyPlain);

    @Override
    default AiClientConfigDto getDecryptedAiClientConfig(Long userId) {
        UserAiConfig config = getDecryptedConfig(userId);
        return new AiClientConfigDto(
            config.getProvider(),
            config.getConfigValue("baseUrl"),
            config.getConfigValue("model"),
            config.getConfigValue("apiKey"),
            config.getConfigValue("reasoningEffort")
        );
    }
}
