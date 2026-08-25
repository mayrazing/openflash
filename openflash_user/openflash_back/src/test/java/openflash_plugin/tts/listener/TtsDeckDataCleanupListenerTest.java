package openflash_plugin.tts.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import openflash_core.spi.DeckDeletedEvent;
import org.junit.jupiter.api.Test;

class TtsDeckDataCleanupListenerTest {

    @Test
    void deletesTtsDeckSettingsForDeletedDeck() {
        AtomicReference<Long> deleted = new AtomicReference<>();
        TtsDeckDataCleanupListener listener = new TtsDeckDataCleanupListener(deckId -> { deleted.set(deckId); return 1; });

        listener.onDeckDeleted(new DeckDeletedEvent(1L, 9L));

        assertEquals(9L, deleted.get());
    }
}
