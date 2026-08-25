package openflash_core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CardChangeEventTest {

    @Test
    void storesUserIdsCardIdsAndKind() {
        CardChangeEvent event = CardChangeEvent.of(7L, List.of(10L, 11L), CardChangeEvent.Kind.CREATED);

        assertEquals(7L, event.userId());
        assertEquals(List.of(10L, 11L), event.cardIds());
        assertEquals(CardChangeEvent.Kind.CREATED, event.kind());
    }

    @Test
    void cardIdsAreImmutable() {
        CardChangeEvent event = CardChangeEvent.of(7L, List.of(10L), CardChangeEvent.Kind.UPDATED);

        assertThrows(UnsupportedOperationException.class, () -> event.cardIds().add(11L));
    }

    @Test
    void normalEventsHaveNoMoveDeckContext() {
        CardChangeEvent event = CardChangeEvent.of(7L, List.of(10L), CardChangeEvent.Kind.UPDATED);

        assertNull(event.sourceDeckId());
        assertNull(event.targetDeckId());
    }

    @Test
    void movedEventStoresSourceAndTargetDeckIds() {
        CardChangeEvent event = CardChangeEvent.moved(7L, List.of(10L, 11L), 1L, 2L);

        assertEquals(7L, event.userId());
        assertEquals(List.of(10L, 11L), event.cardIds());
        assertEquals(CardChangeEvent.Kind.MOVED, event.kind());
        assertEquals(1L, event.sourceDeckId());
        assertEquals(2L, event.targetDeckId());
    }

    @Test
    void movedEventCardIdsAreImmutable() {
        CardChangeEvent event = CardChangeEvent.moved(7L, List.of(10L), 1L, 2L);

        assertThrows(UnsupportedOperationException.class, () -> event.cardIds().add(11L));
    }

    @Test
    void movedEventRequiresSourceAndTargetDeckIds() {
        assertThrows(NullPointerException.class,
            () -> CardChangeEvent.moved(7L, List.of(10L), null, 2L));
        assertThrows(NullPointerException.class,
            () -> CardChangeEvent.moved(7L, List.of(10L), 1L, null));
    }
}
