package openflash_ai_runtime.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import openflash_ai_runtime.mapper.RuntimeSystemConfigMapper;
import openflash_ai_runtime.service.RuntimeSystemConfigService;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 从 pw_system_config 读取 runtime 参数并短缓存查询结果. */
@Service
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class RuntimeSystemConfigServiceImpl implements RuntimeSystemConfigService {

    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final Function<String, String> loader;
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public RuntimeSystemConfigServiceImpl(RuntimeSystemConfigMapper mapper) {
        this((Function<String, String>) mapper::findValueByKey);
    }

    RuntimeSystemConfigServiceImpl(Function<String, String> loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /** 返回 DB 字符串; 缺失时返回旧默认值. */
    public String getString(String key, String defaultValue) {
        String value = load(key);
        return value == null ? defaultValue : value;
    }

    /** 返回 DB long; 缺失或格式错误时返回旧默认值. */
    public long getLong(String key, long defaultValue) {
        String value = load(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return defaultValue;
        }
    }

    private String load(String key) {
        long now = System.currentTimeMillis();
        return cache.compute(key, (ignored, current) -> {
            if (current != null && current.expiresAtMillis() > now) return current;
            return new CachedEntry(loader.apply(key), now + CACHE_TTL_MILLIS);
        }).value();
    }

    private record CachedEntry(String value, long expiresAtMillis) {
    }
}
