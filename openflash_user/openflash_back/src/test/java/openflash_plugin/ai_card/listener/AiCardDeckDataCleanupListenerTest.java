package openflash_plugin.ai_card.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import openflash_core.spi.DeckDeletedEvent;
import org.junit.jupiter.api.Test;

class AiCardDeckDataCleanupListenerTest {

    @Test
    void deletesDeckAiSettingsForDeletedDeck() {
        AtomicReference<Long> deleted = new AtomicReference<>();
        AiCardDeckDataCleanupListener listener = new AiCardDeckDataCleanupListener(deckId -> { deleted.set(deckId); return 1; });

        listener.onDeckDeleted(new DeckDeletedEvent(1L, 9L));

        assertEquals(9L, deleted.get());
    }
}
