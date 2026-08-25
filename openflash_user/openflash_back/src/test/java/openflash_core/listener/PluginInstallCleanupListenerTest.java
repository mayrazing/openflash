package openflash_core.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.spi.DeckDeletedEvent;
import org.junit.jupiter.api.Test;

class PluginInstallCleanupListenerTest {

    @Test
    void deletesInstallRowsForDeletedDeck() {
        AtomicReference<Long> deletedDeck = new AtomicReference<>();
        PluginInstallMapper mapper = new PluginInstallMapper() {
            @Override public java.util.List<String> findPluginIdsByDeck(Long u, Long d) { return java.util.List.of(); }
            @Override public int insert(Long u, Long d, String p) { return 0; }
            @Override public int delete(Long u, Long d, String p) { return 0; }
            @Override public int deleteByDeckId(Long deckId) { deletedDeck.set(deckId); return 2; }
            @Override public int deleteByUserId(Long userId) { return 0; }
            @Override public boolean existsByDeckAndPlugin(Long deckId, String pluginId) { return false; }
        };
        PluginInstallCleanupListener listener = new PluginInstallCleanupListener(mapper);

        listener.onDeckDeleted(new DeckDeletedEvent(1L, 9L));

        assertEquals(9L, deletedDeck.get());
    }
}
