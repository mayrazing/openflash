package openflash_core.service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.mapper.SystemConfigMapper;

/**
 * 读取系统配置，并用短期内存缓存降低数据库访问次数。
 */
@Service
public class SystemConfigService {
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final Function<String, String> loader;
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    /**
     * 使用数据库 mapper 创建系统配置服务。
     */
    @Autowired
    public SystemConfigService(SystemConfigMapper mapper) {
        this((Function<String, String>) mapper::findValueByKey);
    }

    /**
     * 使用自定义加载器创建系统配置服务，供包内测试替换数据库读取。
     */
    SystemConfigService(Function<String, String> loader) {
        this.loader = loader;
    }

    /**
     * 读取字符串配置，数据库无值时返回默认值。
     */
    public String getString(String key, String defaultValue) {
        String value = load(key);
        return value == null ? defaultValue : value;
    }

    /**
     * 读取整数配置，数据库无值时返回默认值。
     */
    public int getInt(String key, int defaultValue) {
        String value = load(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 读取布尔配置，数据库无值时返回默认值。
     */
    public boolean getBool(String key, boolean defaultValue) {
        String value = load(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 读取长整数配置，数据库无值时返回默认值。
     */
    public long getLong(String key, long defaultValue) {
        String value = load(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 读取小数配置，数据库无值时返回默认值。
     */
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String value = load(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 从缓存读取配置；缓存缺失或过期时调用加载器刷新缓存。
     */
    private String load(String key) {
        long now = System.currentTimeMillis();
        CachedEntry cached = cache.compute(key, (ignoredKey, current) -> {
            if (current != null && current.expiresAtMillis > now) {
                return current;
            }

            String value = loader.apply(key);
            return new CachedEntry(value, now + CACHE_TTL_MILLIS);
        });
        return cached.value;
    }

    /**
     * 保存缓存值和过期时间。
     */
    private record CachedEntry(String value, long expiresAtMillis) {
    }
}
