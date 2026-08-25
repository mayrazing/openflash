package openflash_core.entity;

/**
 * 练习反应时间阈值配置，由后端从 pw_system_config 读取后返回给前端。
 */
public record ResponseTimeConfig(
    int timeoutSeconds,
    int grade3SlowThresholdSeconds,
    int grade2SlowThresholdSeconds
) {}
