package openflash_core.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AiErrorCode;
import openflash_core.service.UserAiConfigService;
import openflash_core.entity.UserAiConfig;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.mapper.UserActiveAiSelectionMapper;
import openflash_core.common.AppException;
import openflash_core.common.AppLog;

/**
 * 读写用户 AI 配置；config 以 JSON 字符串存储，保存时加密 Key，读出时解密。
 */
@Service
public class UserAiConfigServiceImpl implements UserAiConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserAiConfigServiceImpl.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROVIDER_KEY_PATTERN =
            "^(?:[a-z0-9][a-z0-9-]{0,63}|_codex_[a-z0-9]{1,13})$";
    private static final Set<String> REASONING_EFFORTS =
            Set.of("low", "medium", "high", "xhigh", "max");

    private final UserAiConfigMapper mapper;
    private final TextEncryptor textEncryptor;
    private final UserActiveAiSelectionMapper activeSelections;

    public UserAiConfigServiceImpl(
            UserAiConfigMapper mapper,
            TextEncryptor textEncryptor,
            UserActiveAiSelectionMapper activeSelections) {
        this.mapper = mapper;
        this.textEncryptor = textEncryptor;
        this.activeSelections = activeSelections;
    }

    @Override
    public List<UserAiConfig> listProviders(Long userId) {
        return mapper.findAllByUserId(userId);
    }

    @Override
    public UserAiConfig findProvider(Long userId, String providerKey) {
        return mapper.findByUserIdAndProvider(userId, providerKey);
    }

    @Override
    public UserAiConfig getDecryptedConfig(Long userId) {
        UserAiConfig row = mapper.findActiveByUserId(userId);
        if (row == null) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        String protocol = row.getConfigValue("protocol");
        if (PROTOCOL_ANTHROPIC.equals(protocol)) {
            row.setConfigJson(decryptApiKey(row.getProvider(), row.getConfigJson()));
            return row;
        }
        throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
    }

    @Override
    @Transactional
    public void saveProvider(Long userId, String providerKey, String displayName, String website,
                             String note, String baseUrl, String apiKeyPlain, String model,
                             String reasoningEffort) {
        validateProviderKey(providerKey);
        rejectReservedProviderKey(providerKey);
        validateProviderFields(displayName, baseUrl, model);
        UserAiConfig existing = mapper.findByUserIdAndProvider(userId, providerKey);
        String apiKeyEnc = resolveApiKeyEnc(providerKey, existing, apiKeyPlain);
        String configJson = buildProviderConfigJson(
                displayName, website, note, baseUrl, apiKeyEnc, model,
                normalizeReasoningEffort(reasoningEffort));
        UserAiConfig row = new UserAiConfig();
        row.setUserId(userId);
        row.setProvider(providerKey);
        row.setConfigJson(configJson);
        row.setActive(existing != null && existing.isActive());
        mapper.upsert(row);
    }

    @Override
    @Transactional
    public void createProvider(Long userId, String displayName, String website,
                               String note, String baseUrl, String apiKeyPlain, String model,
                               String reasoningEffort) {
        String providerKey = nextProviderKey(userId, displayName);
        saveProvider(userId, providerKey, displayName, website, note, baseUrl, apiKeyPlain, model,
                reasoningEffort);
    }

    @Override
    @Transactional
    public void deleteProvider(Long userId, String providerKey) {
        validateProviderKey(providerKey);
        rejectReservedProviderKey(providerKey);
        int deleted = mapper.deleteByUserIdAndProvider(userId, providerKey);
        if (deleted == 0) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        activeSelections.deleteUserProviderSelection(userId, providerKey);
    }

    /**
     * 模型发现 Key 唯一出口：新 Key 非空走明文；否则按 (userId, providerKey) 取该用户自己的行解密。
     * 故意不走 findActiveByUserId，因为编辑未启用 provider 时也要能发现模型；mapper 严格按当前 userId 查询，杜绝越权。
     * providerKey 非空必先校验，避免脏键即使在 plain 短路路径下也参与后续日志/缓存等流程。
     */
    @Override
    public String resolveDiscoveryApiKey(Long userId, String providerKey, String apiKeyPlain) {
        if (providerKey != null) {
            validateProviderKey(providerKey);
        }
        if (apiKeyPlain != null && !apiKeyPlain.isBlank()) {
            return apiKeyPlain;
        }
        if (providerKey == null) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        UserAiConfig row = mapper.findByUserIdAndProvider(userId, providerKey);
        if (row == null) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        String enc = row.getConfigValue("apiKeyEnc");
        if (enc == null || enc.isBlank()) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        try {
            return textEncryptor.decrypt(enc);
        } catch (Exception e) {
            AppLog.error(log, AiErrorCode.AI_KEY_DECRYPT_FAILED, "AI API Key 解密失败，需用户重新保存配置", e);
            throw new AppException(AiErrorCode.AI_KEY_DECRYPT_FAILED);
        }
    }

    /** 校验供应商 key，避免路径、缓存键和 DB 唯一键出现不可控字符。 */
    private void validateProviderKey(String providerKey) {
        if (providerKey == null || !providerKey.matches(PROVIDER_KEY_PATTERN)) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
    }

    /** 根据显示名称生成用户无感的内部 key，并在重名时追加后缀。 */
    private String nextProviderKey(Long userId, String displayName) {
        String base = providerKeyBase(displayName);
        for (int i = 1; i < 100; i++) {
            String suffix = i == 1 ? "" : "-" + i;
            String candidate = truncateForSuffix(base, suffix) + suffix;
            if (!CODEX_PROVIDER_KEY.equals(candidate)
                && mapper.findByUserIdAndProvider(userId, candidate) == null) {
                return candidate;
            }
        }
        throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
    }

    /** 将名称压成 provider 字段允许的短 key；非拉丁名称统一走 custom。 */
    private String providerKeyBase(String displayName) {
        String value = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (value.isBlank()) {
            value = "custom";
        }
        return value.length() > 20 ? value.substring(0, 20).replaceAll("-+$", "") : value;
    }

    /** provider 字段当前长度 20，给重名后缀预留空间。 */
    private String truncateForSuffix(String base, String suffix) {
        int max = 20 - suffix.length();
        String value = base.length() > max ? base.substring(0, max) : base;
        return value.replaceAll("-+$", "");
    }

    /** 校验 Anthropic 供应商必填连接参数。 */
    private void validateProviderFields(String displayName, String baseUrl, String model) {
        if (displayName == null || displayName.isBlank()
            || baseUrl == null || baseUrl.isBlank()
            || model == null || model.isBlank()) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
    }

    /** 阻止通用 API Key 配置入口覆盖或删除内置 Codex 行。 */
    private void rejectReservedProviderKey(String providerKey) {
        if (CODEX_PROVIDER_KEY.equals(providerKey)) {
            throw new AppException(AiErrorCode.AI_PROVIDER_RESERVED);
        }
    }

    /** Key 为空时沿用旧 Key；新 provider 无任何 Key 来源时拒绝保存。 */
    private String resolveApiKeyEnc(String providerKey, UserAiConfig existing, String apiKeyPlain) {
        if (apiKeyPlain != null && !apiKeyPlain.isBlank()) {
            return textEncryptor.encrypt(apiKeyPlain);
        }
        String existingKey = existing == null ? null : existing.getConfigValue("apiKeyEnc");
        if (existingKey != null && !existingKey.isBlank()) {
            return existingKey;
        }
        throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
    }

    /** 构建统一 Anthropic 供应商 JSON。 */
    private String buildProviderConfigJson(String displayName, String website, String note,
                                           String baseUrl, String apiKeyEnc, String model,
                                           String reasoningEffort) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("protocol", PROTOCOL_ANTHROPIC);
        map.put("displayName", displayName);
        map.put("website", website == null ? "" : website);
        map.put("note", note == null ? "" : note);
        map.put("baseUrl", baseUrl);
        map.put("apiKeyEnc", apiKeyEnc);
        map.put("model", model);
        if (reasoningEffort != null) map.put("reasoningEffort", reasoningEffort);
        return serializeConfig(map);
    }

    /** 只允许 Anthropic SDK 当前支持的 effort 档位. */
    private String normalizeReasoningEffort(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!REASONING_EFFORTS.contains(normalized)) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        return normalized;
    }

    /** 按插入顺序序列化连接配置。 */
    private String serializeConfig(Map<String, String> config) {
        try {
            return JSON.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("config JSON 序列化失败", e);
        }
    }

    /** 将 configJson 里的 apiKeyEnc 解密为 apiKey；不泄露给普通列表接口。 */
    private String decryptApiKey(String providerKey, String configJson) {
        try {
            Map<String, String> map = JSON.readValue(configJson, new TypeReference<>() {});
            String enc = map.get("apiKeyEnc");
            if (enc == null || enc.isBlank()) {
                throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
            }
            map.put("apiKey", textEncryptor.decrypt(enc));
            return JSON.writeValueAsString(map);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            AppLog.error(log, AiErrorCode.AI_KEY_DECRYPT_FAILED, "AI API Key 解密失败，需用户重新保存配置", e);
            throw new AppException(AiErrorCode.AI_KEY_DECRYPT_FAILED);
        }
    }
}
