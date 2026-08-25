package openflash_plugin.mask_mode.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import openflash_core.spi.DeckDeletedEvent;
import org.junit.jupiter.api.Test;

class MaskModeDeckDataCleanupListenerTest {

    @Test
    void deletesMaskModeDeckSettingsForDeletedDeck() {
        AtomicReference<Long> deleted = new AtomicReference<>();
        MaskModeDeckDataCleanupListener listener =
                new MaskModeDeckDataCleanupListener(deckId -> {
                    deleted.set(deckId);
                    return 1;
                });

        listener.onDeckDeleted(new DeckDeletedEvent(1L, 9L));

        assertEquals(9L, deleted.get());
    }
}
