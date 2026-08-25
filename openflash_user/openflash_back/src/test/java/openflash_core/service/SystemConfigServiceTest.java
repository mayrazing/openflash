package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SystemConfigServiceTest {

    /**
     * 验证数据库无配置时，各类型读取结果回退到默认值。
     */
    @Test
    void returnsDefaultValuesWhenDatabaseHasNoValue() {
        Function<String, String> loader = key -> null;
        SystemConfigService service = new SystemConfigService(loader);

        assertEquals("default_voice", service.getString("tts.voice", "default_voice"));
        assertEquals(20, service.getInt("async-task.process-batch-size", 20));
        assertEquals(false, service.getBool("ai.think", false));
        assertEquals(30000L, service.getLong("ai.timeout-millis", 30000L));
        assertEquals(new BigDecimal("1.0"), service.getDecimal("tts.speed", new BigDecimal("1.0")));
    }

    /**
     * 验证数据库有配置时，各类型读取结果使用数据库值。
     */
    @Test
    void returnsDatabaseValuesWhenDatabaseHasValue() {
        Function<String, String> loader = key -> switch (key) {
            case "tts.voice" -> "af_sky";
            case "async-task.process-batch-size" -> "50";
            case "ai.think" -> "true";
            case "ai.timeout-millis" -> "60000";
            case "tts.speed" -> "1.5";
            default -> null;
        };
        SystemConfigService service = new SystemConfigService(loader);

        assertEquals("af_sky", service.getString("tts.voice", "default_voice"));
        assertEquals(50, service.getInt("async-task.process-batch-size", 20));
        assertEquals(true, service.getBool("ai.think", false));
        assertEquals(60000L, service.getLong("ai.timeout-millis", 30000L));
        assertEquals(new BigDecimal("1.5"), service.getDecimal("tts.speed", new BigDecimal("1.0")));
    }

    /**
     * 验证数据库数字配置填错时，用户操作仍回退默认值而不是直接失败。
     */
    @Test
    void returnsDefaultValuesWhenDatabaseNumberIsInvalid() {
        Function<String, String> loader = key -> "bad-number";
        SystemConfigService service = new SystemConfigService(loader);

        assertEquals(20, service.getInt("async-task.process-batch-size", 20));
        assertEquals(30000L, service.getLong("ai.timeout-millis", 30000L));
        assertEquals(new BigDecimal("1.0"), service.getDecimal("tts.speed", new BigDecimal("1.0")));
    }

    /**
     * 验证 60 秒缓存期内，同一个配置只触发一次加载。
     */
    @Test
    void reusesCachedValueWithinCacheTtl() {
        AtomicInteger loadCount = new AtomicInteger();
        Function<String, String> loader = key -> {
            loadCount.incrementAndGet();
            return "cached_value";
        };
        SystemConfigService service = new SystemConfigService(loader);

        assertEquals("cached_value", service.getString("demo.key", "default"));
        assertEquals("cached_value", service.getString("demo.key", "default"));
        assertEquals(1, loadCount.get());
    }

    /**
     * 验证数据库值变化后，60 秒缓存期内仍返回旧缓存值。
     */
    @Test
    void keepsOldCachedValueWhenDatabaseValueChangesWithinCacheTtl() {
        AtomicReference<String> databaseValue = new AtomicReference<>("af_heart");
        AtomicInteger loadCount = new AtomicInteger();
        Function<String, String> loader = key -> {
            loadCount.incrementAndGet();
            return databaseValue.get();
        };
        SystemConfigService service = new SystemConfigService(loader);

        assertEquals("af_heart", service.getString("tts.voice", "default_voice"));
        databaseValue.set("af_sky");
        assertEquals("af_heart", service.getString("tts.voice", "default_voice"));
        assertEquals(1, loadCount.get());
    }
}
