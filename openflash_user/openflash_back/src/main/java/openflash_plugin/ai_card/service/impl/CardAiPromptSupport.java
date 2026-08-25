package openflash_plugin.ai_card.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import openflash_core.common.AppException;
import openflash_plugin.ai_card.common.AiCardErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.service.impl.EffectiveAiProfileResolver.ActiveAiIdentity;
import openflash_core.entity.Card;

/**
 * 统一维护 AI prompt、side 和指纹规则。
 */
public final class CardAiPromptSupport {

    public static final String SIDE_A = "A";
    public static final String SIDE_B = "B";

    private CardAiPromptSupport() {
    }

    /**
     * 规范化卡面标识，只有 B/b 走 B 面，其余输入回退 A 面。
     */
    public static String normalizeSide(String side) {
        return "b".equalsIgnoreCase(side) || SIDE_B.equalsIgnoreCase(side) ? SIDE_B : SIDE_A;
    }

    /**
     * 按卡面取出待生成内容，空内容直接返回业务错误。
     */
    public static String normalizePrompt(Card card, String side) {
        boolean useB = SIDE_B.equals(normalizeSide(side));
        String raw = useB ? card.getSideB() : card.getSideA();
        String prompt = raw == null ? "" : raw.trim();
        if (prompt.isEmpty()) {
            throw new AppException(AiCardErrorCode.AI_CARD_SIDE_BLANK);
        }
        return prompt;
    }

    /**
     * 用 prompt 文本创建默认 AI 缓存指纹。
     */
    public static String buildFingerprint(String prompt) {
        return buildFingerprint(prompt, "");
    }

    /**
     * 只用 prompt 文本创建 AI 缓存指纹；profileName 保留入参兼容旧调用。
     */
    public static String buildFingerprint(String prompt, String profileName) {
        return buildFingerprintBySignature(prompt, "");
    }

    /**
     * 只用 prompt 文本创建 AI 缓存指纹；profile 保留入参兼容旧调用。
     */
    public static String buildFingerprint(String prompt, AiProperties.AiProfile profile) {
        return buildFingerprint(prompt);
    }

    /**
     * 只用 prompt 文本创建 AI 缓存指纹；profile/userId 保留入参兼容旧调用。
     */
    public static String buildFingerprint(String prompt, AiProperties.AiProfile profile, Long userId) {
        return buildFingerprint(prompt);
    }

    /**
     * 只用 prompt 文本创建 AI 缓存指纹；active AI 身份不影响缓存命中。
     */
    public static String buildFingerprint(String prompt, ActiveAiIdentity identity) {
        return buildFingerprint(prompt);
    }

    /**
     * 创建 source profile 的副本，将 system 替换为指定值（可为 null）。
     */
    public static AiProperties.AiProfile withSystem(AiProperties.AiProfile source, String system) {
        if (source == null) {
            return null;
        }
        AiProperties.AiProfile copy = new AiProperties.AiProfile();
        copy.setName(source.getName());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setSystem(system);
        return copy;
    }

    /**
     * 按签名内容和 prompt 文本创建 SHA-256 指纹。
     */
    private static String buildFingerprintBySignature(String prompt, String profileSignature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String fingerprintSource = lengthPrefixed(profileSignature) + lengthPrefixed(prompt);
            byte[] bytes = digest.digest(fingerprintSource.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 将 null 字段转为空字符串，保证指纹拼接稳定。
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 用长度前缀保留字段边界，避免字段内容里的换行影响缓存身份。
     */
    private static String lengthPrefixed(String value) {
        String safeValue = safe(value);
        return safeValue.length() + ":" + safeValue;
    }
}
