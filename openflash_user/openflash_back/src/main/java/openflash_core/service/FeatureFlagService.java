package openflash_core.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.mapper.FeatureFlagMapper;
import openflash_core.mapper.UserFeatureFlagMapper;

/**
 * 读取功能开关，并用短期内存缓存降低全局开关数据库访问次数。
 */
@Service
public class FeatureFlagService {
    static final long CACHE_TTL_MILLIS = 60_000L;

    private final FeatureFlagMapper mapper;
    private final UserFeatureFlagMapper userMapper;
    private final ConcurrentHashMap<String, CachedFlag> globalCache = new ConcurrentHashMap<>();

    /**
     * 使用全局和用户级 mapper 创建生产功能开关服务。
     */
    @Autowired
    public FeatureFlagService(FeatureFlagMapper mapper, UserFeatureFlagMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    /** 保留一参数构造器，使现有 lambda 调用继续只读取全局开关。 */
    public FeatureFlagService(FeatureFlagMapper mapper) {
        this(mapper, UserFeatureFlagMapper.noOverrides());
    }

    /**
     * 读取全局功能开关，数据库无值时默认启用。
     */
    public boolean isEnabled(String featureKey) {
        Boolean enabled = loadGlobal(featureKey);
        return enabled == null || enabled;
    }

    /** 根据 rollout 类型解析指定用户的最终功能开关值。 */
    public boolean isEnabledForUser(String featureKey, Long userId) {
        Boolean global = loadGlobal(featureKey);
        boolean globalValue = global == null || global;
        if (userId == null || !"USER_OVERRIDE".equals(userMapper.findRolloutType(featureKey))) {
            return globalValue;
        }
        Boolean userValue = userMapper.findUserEnabled(featureKey, userId);
        return userValue == null ? globalValue : userValue;
    }

    /**
     * 从缓存读取全局开关；缓存缺失或过期时调用加载器刷新缓存。
     */
    private Boolean loadGlobal(String featureKey) {
        long now = System.currentTimeMillis();
        CachedFlag cached = globalCache.compute(featureKey, (ignoredKey, current) -> {
            if (current != null && current.expiresAtMillis > now) {
                return current;
            }

            Boolean enabled = mapper.findGlobalEnabled(featureKey);
            return new CachedFlag(enabled, now + CACHE_TTL_MILLIS);
        });
        return cached.enabled;
    }

    /**
     * 保存缓存开关值和过期时间。
     */
    private record CachedFlag(Boolean enabled, long expiresAtMillis) {
    }
}
