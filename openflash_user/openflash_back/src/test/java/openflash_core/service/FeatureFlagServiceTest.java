package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import openflash_core.mapper.FeatureFlagMapper;
import openflash_core.mapper.UserFeatureFlagMapper;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest {

    /**
     * 验证数据库无全局配置时，功能默认启用。
     */
    @Test
    void returnsTrueWhenGlobalFlagDoesNotExistInDatabase() {
        FeatureFlagService service = new FeatureFlagService(key -> null);

        assertTrue(service.isEnabled("feature.tts"));
    }

    /**
     * 验证全局开关关闭时，功能返回关闭。
     */
    @Test
    void returnsFalseWhenGlobalFlagIsFalse() {
        FeatureFlagService service = new FeatureFlagService(key -> false);

        assertFalse(service.isEnabled("feature.tts"));
    }

    /**
     * 验证 60 秒缓存期内，同一个全局开关只触发一次加载。
     */
    @Test
    void reusesGlobalFlagCacheWithinCacheTtl() {
        AtomicInteger globalLoadCount = new AtomicInteger();
        FeatureFlagMapper mapper = key -> {
            globalLoadCount.incrementAndGet();
            return true;
        };
        FeatureFlagService service = new FeatureFlagService(mapper);

        assertTrue(service.isEnabled("feature.tts"));
        assertTrue(service.isEnabled("feature.tts"));

        assertEquals(1, globalLoadCount.get());
    }

    @Test
    void userOverrideTrueEnablesFeatureWhenGlobalFlagIsFalse() {
        FeatureFlagMapper mapper = mock(FeatureFlagMapper.class);
        UserFeatureFlagMapper userMapper = mock(UserFeatureFlagMapper.class);
        when(mapper.findGlobalEnabled("feature.test")).thenReturn(false);
        when(userMapper.findRolloutType("feature.test")).thenReturn("USER_OVERRIDE");
        when(userMapper.findUserEnabled("feature.test", 7L)).thenReturn(true);
        FeatureFlagService service = new FeatureFlagService(mapper, userMapper);

        assertTrue(service.isEnabledForUser("feature.test", 7L));
    }

    @Test
    void userOverrideFalseDisablesFeatureWhenGlobalFlagIsTrue() {
        FeatureFlagMapper mapper = mock(FeatureFlagMapper.class);
        UserFeatureFlagMapper userMapper = mock(UserFeatureFlagMapper.class);
        when(mapper.findGlobalEnabled("feature.test")).thenReturn(true);
        when(userMapper.findRolloutType("feature.test")).thenReturn("USER_OVERRIDE");
        when(userMapper.findUserEnabled("feature.test", 7L)).thenReturn(false);
        FeatureFlagService service = new FeatureFlagService(mapper, userMapper);

        assertFalse(service.isEnabledForUser("feature.test", 7L));
    }

    @Test
    void missingUserOverrideFallsBackToGlobalFlag() {
        FeatureFlagMapper mapper = mock(FeatureFlagMapper.class);
        UserFeatureFlagMapper userMapper = mock(UserFeatureFlagMapper.class);
        when(mapper.findGlobalEnabled("feature.test")).thenReturn(false);
        when(userMapper.findRolloutType("feature.test")).thenReturn("USER_OVERRIDE");
        when(userMapper.findUserEnabled("feature.test", 7L)).thenReturn(null);
        FeatureFlagService service = new FeatureFlagService(mapper, userMapper);

        assertFalse(service.isEnabledForUser("feature.test", 7L));
    }

    @Test
    void globalRolloutIgnoresUserOverrideRow() {
        FeatureFlagMapper mapper = mock(FeatureFlagMapper.class);
        UserFeatureFlagMapper userMapper = mock(UserFeatureFlagMapper.class);
        when(mapper.findGlobalEnabled("feature.test")).thenReturn(true);
        when(userMapper.findRolloutType("feature.test")).thenReturn("GLOBAL");
        when(userMapper.findUserEnabled("feature.test", 7L)).thenReturn(false);
        FeatureFlagService service = new FeatureFlagService(mapper, userMapper);

        assertTrue(service.isEnabledForUser("feature.test", 7L));
        verify(userMapper, never()).findUserEnabled("feature.test", 7L);
    }

    @Test
    void missingGlobalRowPreservesDefaultEnabledBehaviorForUser() {
        FeatureFlagMapper mapper = mock(FeatureFlagMapper.class);
        UserFeatureFlagMapper userMapper = mock(UserFeatureFlagMapper.class);
        when(mapper.findGlobalEnabled("feature.test")).thenReturn(null);
        when(userMapper.findRolloutType("feature.test")).thenReturn("GLOBAL");
        FeatureFlagService service = new FeatureFlagService(mapper, userMapper);

        assertTrue(service.isEnabledForUser("feature.test", 7L));
    }
}
