package openflash_admin.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.mapper.AdminSystemConfigMapper;

/** 限制管理员账号和来源在固定时间窗内的失败登录次数. */
@Component
public class AdminLoginAttemptGuard {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_WINDOW_MILLIS = 15L * 60 * 1000;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final long CONFIG_CACHE_MILLIS = 60_000L;

    private final Function<String, String> configLoader;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    @Autowired
    public AdminLoginAttemptGuard(AdminSystemConfigMapper mapper) {
        this((Function<String, String>) mapper::findValueByKey);
    }

    private AdminLoginAttemptGuard(Function<String, String> configLoader) {
        this.configLoader = configLoader;
    }

    public static AdminLoginAttemptGuard fromConfigLoader(Function<String, String> configLoader) {
        return new AdminLoginAttemptGuard(configLoader);
    }

    public AttemptLease beginAttempt(String username) {
        long now = System.currentTimeMillis();
        long windowMillis = positiveLong("auth.login.window-millis", DEFAULT_WINDOW_MILLIS);
        List<String> keys = keys(username);
        synchronized (attempts) {
            attempts.entrySet().removeIf(entry -> entry.getValue().inFlight == 0
                    && entry.getValue().startedAtMillis + windowMillis <= now);
            long missingKeys = keys.stream().filter(key -> !attempts.containsKey(key)).count();
            if (attempts.size() + missingKeys > MAX_TRACKED_KEYS) {
                throw limited();
            }

            int maxAttempts = positiveInt("auth.login.max-attempts", DEFAULT_MAX_ATTEMPTS);
            for (String key : keys) {
                AttemptWindow window = activeWindow(key, now, windowMillis);
                if (window.failures + window.inFlight >= maxAttempts) {
                    throw limited();
                }
            }
            for (String key : keys) {
                activeWindow(key, now, windowMillis).inFlight++;
            }
        }
        return new AttemptLease(keys);
    }

    private List<String> keys(String username) {
        return List.of("account:" + normalizedAccount(username), "source:" + currentSource());
    }

    private String normalizedAccount(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private int positiveInt(String key, int defaultValue) {
        try {
            int value = Integer.parseInt(loadConfig(key));
            return value > 0 ? value : defaultValue;
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private long positiveLong(String key, long defaultValue) {
        try {
            long value = Long.parseLong(loadConfig(key));
            return value > 0 ? value : defaultValue;
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private String loadConfig(String key) {
        long now = System.currentTimeMillis();
        return configCache.compute(key, (ignored, current) -> {
            if (current != null && current.expiresAtMillis > now) {
                return current;
            }
            return new CachedConfig(configLoader.apply(key), now + CONFIG_CACHE_MILLIS);
        }).value;
    }

    private String currentSource() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            if (request.getRemoteAddr() != null && !request.getRemoteAddr().isBlank()) {
                return request.getRemoteAddr();
            }
        }
        return "unknown";
    }

    private AdminException limited() {
        return new AdminException(AdminErrorCode.LOGIN_RATE_LIMITED);
    }

    private AttemptWindow activeWindow(String key, long now, long windowMillis) {
        AttemptWindow window = attempts.computeIfAbsent(key, ignored -> new AttemptWindow(now));
        if (window.startedAtMillis + windowMillis <= now) {
            window.failures = 0;
            window.startedAtMillis = now;
        }
        return window;
    }

    private void settle(List<String> keys, Outcome outcome) {
        long now = System.currentTimeMillis();
        long windowMillis = positiveLong("auth.login.window-millis", DEFAULT_WINDOW_MILLIS);
        synchronized (attempts) {
            for (String key : keys) {
                AttemptWindow window = attempts.get(key);
                if (window == null) continue;
                window.inFlight = Math.max(0, window.inFlight - 1);
                if (window.startedAtMillis + windowMillis <= now) {
                    window.failures = 0;
                    window.startedAtMillis = now;
                }
                if (outcome == Outcome.FAILURE) {
                    window.failures++;
                } else if (outcome == Outcome.SUCCESS && key.startsWith("account:")) {
                    window.failures = 0;
                }
                if (window.failures == 0 && window.inFlight == 0) {
                    attempts.remove(key, window);
                }
            }
        }
    }

    public final class AttemptLease implements AutoCloseable {
        private final List<String> keys;
        private final AtomicBoolean settled = new AtomicBoolean();

        private AttemptLease(List<String> keys) {
            this.keys = keys;
        }

        public void recordFailure() {
            complete(Outcome.FAILURE);
        }

        public void recordSuccess() {
            complete(Outcome.SUCCESS);
        }

        @Override
        public void close() {
            complete(Outcome.RELEASE_ONLY);
        }

        private void complete(Outcome outcome) {
            if (settled.compareAndSet(false, true)) {
                settle(keys, outcome);
            }
        }
    }

    private enum Outcome { FAILURE, SUCCESS, RELEASE_ONLY }

    private static final class AttemptWindow {
        private int failures;
        private int inFlight;
        private long startedAtMillis;

        private AttemptWindow(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }

    private record CachedConfig(String value, long expiresAtMillis) {}
}
