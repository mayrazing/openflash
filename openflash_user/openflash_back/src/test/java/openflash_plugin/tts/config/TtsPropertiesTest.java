package openflash_plugin.tts.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import openflash_core.service.SystemConfigService;

class TtsPropertiesTest {

    /**
     * 验证 TTS 请求参数优先读取数据库配置，数据库无值时仍由服务回退 YAML 默认值。
     */
    @Test
    void dynamicGettersReadSystemConfigService() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getString("tts.voice", "default")).thenReturn("reference_voice");
        when(systemConfigService.getDecimal("tts.speed", BigDecimal.valueOf(0.95d)))
                .thenReturn(BigDecimal.valueOf(0.9d));
        when(systemConfigService.getDecimal("tts.piper-speed", BigDecimal.valueOf(0.7d)))
                .thenReturn(BigDecimal.valueOf(0.65d));
        when(systemConfigService.getString("tts.accent", "american")).thenReturn("AMERICAN");
        when(systemConfigService.getString("tts.engine-version", "cosyvoice3-rl-fp16"))
                .thenReturn("cosyvoice3-rl-fp16-v2");
        when(systemConfigService.getString(
                "tts.piper-engine-version", "piper-1.6.0-libritts-r-medium-speaker-0"))
                .thenReturn("piper-1.6.0-libritts-r-medium-speaker-0-v2");
        when(systemConfigService.getInt("tts.max-concurrent-requests", 1)).thenReturn(2);
        when(systemConfigService.getInt("tts.request-queue-capacity", 1)).thenReturn(3);
        when(systemConfigService.getInt("tts.max-concurrent-requests-per-user", 2)).thenReturn(4);
        when(systemConfigService.getLong("tts.connect-timeout-millis", 5000L)).thenReturn(7000L);
        when(systemConfigService.getLong("tts.request-timeout-millis", 10000L)).thenReturn(15000L);
        TtsProperties properties = new TtsProperties();
        properties.setVoice("default");
        properties.setSpeed(0.95d);
        properties.setPiperSpeed(0.7d);
        properties.setAccent("american");
        properties.setEngineVersion("cosyvoice3-rl-fp16");
        properties.setPiperEngineVersion("piper-1.6.0-libritts-r-medium-speaker-0");
        properties.setMaxConcurrentRequests(1);
        properties.setRequestQueueCapacity(1);
        properties.setMaxConcurrentRequestsPerUser(2);
        properties.setConnectTimeoutMillis(5000L);
        properties.setRequestTimeoutMillis(10000L);
        properties.setSystemConfigService(systemConfigService);

        assertEquals("reference_voice", properties.getVoice());
        assertEquals(0.9d, properties.getSpeed());
        assertEquals(0.65d, properties.getPiperSpeed());
        assertEquals("american", properties.getAccent());
        assertEquals("cosyvoice3-rl-fp16-v2", properties.getEngineVersion());
        assertEquals("piper-1.6.0-libritts-r-medium-speaker-0-v2",
                properties.getPiperEngineVersion());
        assertEquals(2, properties.getMaxConcurrentRequests());
        assertEquals(3, properties.getRequestQueueCapacity());
        assertEquals(4, properties.getMaxConcurrentRequestsPerUser());
        assertEquals(7000L, properties.getConnectTimeoutMillis());
        assertEquals(15000L, properties.getRequestTimeoutMillis());
    }

    /**
     * 验证 TTS 并发配置读到无效值时继续使用 YAML 默认值，避免服务重启失败。
     */
    @Test
    void maxConcurrentRequestsFallsBackWhenDatabaseValueIsInvalid() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getInt("tts.max-concurrent-requests", 1)).thenReturn(0);
        TtsProperties properties = new TtsProperties();
        properties.setMaxConcurrentRequests(1);
        properties.setSystemConfigService(systemConfigService);

        assertEquals(1, properties.getMaxConcurrentRequests());
    }

    @Test
    void accentFallsBackToAmericanWhenDatabaseValueIsUnsupported() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getString("tts.accent", "american")).thenReturn("british");
        TtsProperties properties = new TtsProperties();
        properties.setAccent("american");
        properties.setSystemConfigService(systemConfigService);

        assertEquals("american", properties.getAccent());
    }
}
