package openflash_core.controller;

import openflash_core.service.impl.PluginRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import openflash_core.mapper.FeatureFlagMapper;
import openflash_core.service.FeatureFlagService;
import openflash_core.service.PluginInstallService;
import org.junit.jupiter.api.Test;

class PluginControllerInstallTest {

    private static final FeatureFlagMapper enabledMapper = new FeatureFlagMapper() {
        @Override public Boolean findGlobalEnabled(String featureKey) { return true; }
    };

    /** 记录装/卸调用的探针 service（继承真实类，覆写方法）。 */
    static class SpyInstall extends PluginInstallService {
        final List<String> calls = new ArrayList<>();
        SpyInstall() { super(null, new PluginRegistry(List.of()), null); }
        @Override public void install(Long u, Long d, String p) { calls.add("install:" + d + ":" + p); }
        @Override public void uninstall(Long u, Long d, String p) { calls.add("uninstall:" + d + ":" + p); }
        @Override public List<String> installedPluginIds(Long u, Long d) { return List.of(); }
    }

    @Test
    void installAppliesToCheckedDecksAndUninstallsUnchecked() {
        SpyInstall spy = new SpyInstall();
        openflash_core.service.CurrentUserService user = new openflash_core.service.CurrentUserService() {
            @Override public Long getCurrentUserId() { return 1L; }
            @Override public openflash_core.entity.User getCurrentUser() { return null; }
            @Override public void login(openflash_core.entity.User u) {}
            @Override public void logout() {}
            @Override public void ensureUserSettings(Long userId) {}
        };
        PluginController controller = new PluginController(new PluginRegistry(List.of()), user, spy, null, new FeatureFlagService(enabledMapper));

        controller.install(new PluginController.InstallRequest("tts", List.of(9L, 10L), List.of(11L)));

        assertTrue(spy.calls.contains("install:9:tts"));
        assertTrue(spy.calls.contains("install:10:tts"));
        assertTrue(spy.calls.contains("uninstall:11:tts"));
        assertEquals(3, spy.calls.size());
    }

    @Test
    void installToleratesNullDeckLists() {
        SpyInstall spy = new SpyInstall();
        openflash_core.service.CurrentUserService user = new openflash_core.service.CurrentUserService() {
            @Override public Long getCurrentUserId() { return 1L; }
            @Override public openflash_core.entity.User getCurrentUser() { return null; }
            @Override public void login(openflash_core.entity.User u) {}
            @Override public void logout() {}
            @Override public void ensureUserSettings(Long userId) {}
        };
        PluginController controller = new PluginController(new PluginRegistry(List.of()), user, spy, null, new FeatureFlagService(enabledMapper));

        // 缺省两组卡包列表（null）不应抛 NPE，且不触发任何装卸调用。
        controller.install(new PluginController.InstallRequest("tts", null, null));

        assertEquals(0, spy.calls.size());
    }
}
