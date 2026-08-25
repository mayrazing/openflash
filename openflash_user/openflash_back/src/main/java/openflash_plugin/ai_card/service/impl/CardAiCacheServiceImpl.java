package openflash_plugin.ai_card.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.config.AsyncTaskProperties;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.mapper.CardAiCacheMapper;
import openflash_plugin.ai_card.service.CardAiCacheService;

/**
 * 统一管理卡片 AI 结果缓存的读写。
 */
@Service
public class CardAiCacheServiceImpl implements CardAiCacheService {

    private final CardAiCacheMapper cardAiCacheMapper;
    private final AsyncTaskProperties asyncTaskProperties;

    @Autowired
    public CardAiCacheServiceImpl(CardAiCacheMapper cardAiCacheMapper, AsyncTaskProperties asyncTaskProperties) {
        this.cardAiCacheMapper = cardAiCacheMapper;
        this.asyncTaskProperties = asyncTaskProperties;
    }

    /**
     * 仅在真实服务命中时刷新访问时间，后台探测不能续命。
     */
    @Override
    public CardAiCache findUsableCacheAndTouchOnServe(Long ownerUserId, String fingerprint) {
        requireOwner(ownerUserId);
        CardAiCache cache = cardAiCacheMapper.findByFingerprint(ownerUserId, fingerprint);
        if (cache == null || cache.getContent() == null || cache.getContent().isBlank()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        cardAiCacheMapper.touchAccessedAtIfStale(
            ownerUserId,
            fingerprint,
            now,
            now.minusHours(asyncTaskProperties.getCacheTouchMinIntervalHours())
        );
        cache.setLastAccessedAt(now);
        return cache;
    }

    /**
     * 后台只判定是否已有可用缓存，不刷新访问时间。
     */
    @Override
    public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
        requireOwner(ownerUserId);
        CardAiCache cache = cardAiCacheMapper.findByFingerprint(ownerUserId, fingerprint);
        if (cache == null || cache.getContent() == null || cache.getContent().isBlank()) {
            return null;
        }
        return cache;
    }

    /**
     * 用户请求链路生成成功后，同时刷新生成时间和访问时间。
     */
    @Override
    public void saveReadyFromServe(Long ownerUserId, String fingerprint, String prompt, String content, Boolean thinkUsed) {
        requireOwner(ownerUserId);
        LocalDateTime now = LocalDateTime.now();
        cardAiCacheMapper.saveReady(ownerUserId, fingerprint, buildPromptFingerprint(prompt), prompt, content, thinkUsed, now, now);
    }

    /**
     * 后台生成成功只更新生成时间，不给缓存续命。
     */
    @Override
    public void saveReadyFromBackground(Long ownerUserId, String fingerprint, String prompt, String content, Boolean thinkUsed) {
        requireOwner(ownerUserId);
        cardAiCacheMapper.saveReady(ownerUserId, fingerprint, buildPromptFingerprint(prompt), prompt, content, thinkUsed, LocalDateTime.now(), null);
    }

    /**
     * 删除超出 TTL 的冷缓存。
     */
    @Override
    public int deleteExpired(LocalDateTime before, int limit) {
        return cardAiCacheMapper.deleteExpired(before, limit);
    }

    private void requireOwner(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("AI cache ownerUserId is required");
        }
    }

    /**
     * 用 prompt 本身生成稳定指纹，记录同一页面词条的归一身份。
     */
    private String buildPromptFingerprint(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizePromptIdentity(prompt).getBytes(StandardCharsets.UTF_8));
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
     * 把用户眼里的同一个输入归一成同一个缓存身份。
     */
    private String normalizePromptIdentity(String prompt) {
        return (prompt == null ? "" : prompt)
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
