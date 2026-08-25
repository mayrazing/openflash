package openflash_core.dto;

/** 创建用户 AI 客户端所需的解密连接快照. */
public record AiClientConfigDto(
        String provider, String baseUrl, String model, String apiKey, String reasoningEffort) {
    public AiClientConfigDto(String provider, String baseUrl, String model, String apiKey) {
        this(provider, baseUrl, model, apiKey, null);
    }
}
