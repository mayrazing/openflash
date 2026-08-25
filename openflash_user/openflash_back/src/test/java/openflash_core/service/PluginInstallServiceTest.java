package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.service.impl.PluginRegistry;
import openflash_core.spi.PluginDescriptor;
import org.junit.jupiter.api.Test;

class PluginInstallServiceTest {

    /** 手写 fake mapper，记录装/卸调用并支持预置已装数据。 */
    static class FakeMapper implements PluginInstallMapper {
        final List<String> installed = new ArrayList<>();
        @Override public List<String> findPluginIdsByDeck(Long userId, Long deckId) { return List.copyOf(installed); }
        @Override public int insert(Long userId, Long deckId, String pluginId) {
            if (!installed.contains(pluginId)) { installed.add(pluginId); return 1; } return 0;
        }
        @Override public int delete(Long userId, Long deckId, String pluginId) {
            return installed.remove(pluginId) ? 1 : 0;
        }
        @Override public int deleteByDeckId(Long deckId) { int n = installed.size(); installed.clear(); return n; }
        @Override public int deleteByUserId(Long userId) { return 0; }
        @Override public boolean existsByDeckAndPlugin(Long deckId, String pluginId) {
            return installed.contains(pluginId);
        }
    }

    /** 手写 fake DeckMapper：owns=true 时视为卡包归当前用户，false 时返回 null（不存在/越权）。 */
    static class FakeDeckMapper implements DeckMapper {
        boolean owns = true;
        @Override public Deck findByIdAndUserId(Long id, Long userId) { return owns ? new Deck() : null; }
        @Override public List<Deck> findByUserId(Long userId) { return List.of(); }
        @Override public List<Long> findIdsByUserIdIncludingDeleted(Long userId) { return List.of(); }
        @Override public Deck findById(Long id) { return null; }
        @Override public int insert(Deck deck) { return 0; }
        @Override public int updateName(Long id, Long userId, String name) { return 0; }
        @Override public int deleteById(Long id, Long userId) { return 0; }
    }

    record StubPlugin(String id, boolean enabled) implements PluginDescriptor {
        @Override public String pluginId() { return id; }
        @Override public boolean isEnabled() { return enabled; }
    }

    private PluginInstallService service(FakeMapper mapper, PluginDescriptor... plugins) {
        return new PluginInstallService(mapper, new PluginRegistry(List.of(plugins)), new FakeDeckMapper());
    }

    private PluginInstallService service(FakeMapper mapper, FakeDeckMapper deckMapper, PluginDescriptor... plugins) {
        return new PluginInstallService(mapper, new PluginRegistry(List.of(plugins)), deckMapper);
    }

    @Test
    void installedReturnsIntersectionOfDeckInstallsAndGloballyEnabled() {
        FakeMapper mapper = new FakeMapper();
        mapper.installed.add("tts");
        mapper.installed.add("ai-card");
        // tts 全局开，ai-card 全局关 → 只返回 tts
        PluginInstallService svc = service(mapper, new StubPlugin("tts", true), new StubPlugin("ai-card", false));

        assertEquals(List.of("tts"), svc.installedPluginIds(1L, 9L));
    }

    @Test
    void installAddsRow() {
        FakeMapper mapper = new FakeMapper();
        PluginInstallService svc = service(mapper, new StubPlugin("tts", true));

        svc.install(1L, 9L, "tts");

        assertTrue(mapper.installed.contains("tts"));
    }

    @Test
    void uninstallRemovesRow() {
        FakeMapper mapper = new FakeMapper();
        mapper.installed.add("tts");
        PluginInstallService svc = service(mapper, new StubPlugin("tts", true));

        svc.uninstall(1L, 9L, "tts");

        assertTrue(mapper.installed.isEmpty());
    }

    @Test
    void installRejectsDeckNotOwnedByUser() {
        FakeMapper mapper = new FakeMapper();
        FakeDeckMapper deckMapper = new FakeDeckMapper();
        deckMapper.owns = false; // 卡包不属于当前用户
        PluginInstallService svc = service(mapper, deckMapper, new StubPlugin("tts", true));

        AppException ex = assertThrows(AppException.class, () -> svc.install(1L, 999L, "tts"));
        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
        assertTrue(mapper.installed.isEmpty()); // 越权时不应写入
    }

    @Test
    void installedRejectsDeckNotOwnedByUser() {
        FakeMapper mapper = new FakeMapper();
        FakeDeckMapper deckMapper = new FakeDeckMapper();
        deckMapper.owns = false;
        PluginInstallService svc = service(mapper, deckMapper, new StubPlugin("tts", true));

        AppException ex = assertThrows(AppException.class, () -> svc.installedPluginIds(1L, 999L));
        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void installRejectsUnsupportedPluginId() {
        FakeMapper mapper = new FakeMapper();
        // 只注册 tts；安装未注册的 'bogus' 应被拒
        PluginInstallService svc = service(mapper, new StubPlugin("tts", true));

        AppException ex = assertThrows(AppException.class, () -> svc.install(1L, 9L, "bogus"));
        assertEquals(ErrorCode.PLUGIN_NOT_SUPPORTED, ex.getErrorCode());
        assertTrue(mapper.installed.isEmpty());
    }

    @Test
    void isInstalledOnDeckReturnsTrueWhenRowExists() {
        FakeMapper mapper = new FakeMapper();
        mapper.installed.add("ai-card");
        PluginInstallService svc = service(mapper, new StubPlugin("ai-card", true));

        assertTrue(svc.isInstalledOnDeck(9L, "ai-card"));
    }

    @Test
    void isInstalledOnDeckReturnsFalseWhenRowMissing() {
        FakeMapper mapper = new FakeMapper();
        PluginInstallService svc = service(mapper, new StubPlugin("ai-card", true));

        assertFalse(svc.isInstalledOnDeck(9L, "ai-card"));
    }
}
