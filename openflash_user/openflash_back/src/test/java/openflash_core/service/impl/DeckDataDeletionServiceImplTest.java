package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.PracticeSessionStoreMapper;
import openflash_core.spi.DeckDeletedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class DeckDataDeletionServiceImplTest {

    @Test
    void deletesAllCoreRowsAndPublishesEventWithoutFileDependency() {
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        CardMapper cardMapper = mock(CardMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        PracticeSessionStoreMapper practiceSessionStoreMapper = mock(PracticeSessionStoreMapper.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        DeckDataDeletionServiceImpl service = new DeckDataDeletionServiceImpl(
            cardProgressMapper,
            cardMediaMapper,
            cardMapper,
            deckSettingsMapper,
            practiceSessionStoreMapper,
            deckMapper,
            eventPublisher
        );
        when(deckMapper.deleteById(11L, 8L)).thenReturn(1);

        service.deleteOwnedDeck(8L, 11L);

        InOrder order = inOrder(cardProgressMapper, cardMediaMapper, cardMapper,
            deckSettingsMapper, practiceSessionStoreMapper, deckMapper, eventPublisher);
        order.verify(cardProgressMapper).deleteByDeckId(11L);
        order.verify(cardMediaMapper).deleteByDeckId(11L);
        order.verify(cardMapper).deleteByDeckId(11L);
        order.verify(deckSettingsMapper).deleteByDeckId(11L);
        order.verify(practiceSessionStoreMapper).deleteByDeckId(11L);
        order.verify(deckMapper).deleteById(11L, 8L);
        order.verify(eventPublisher).publishEvent(new DeckDeletedEvent(8L, 11L));
        order.verifyNoMoreInteractions();
    }

    @Test
    void ownerMismatchThrowsDeckNotFoundWithoutPublishingEvent() {
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        CardMapper cardMapper = mock(CardMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        PracticeSessionStoreMapper practiceSessionStoreMapper = mock(PracticeSessionStoreMapper.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(deckMapper.deleteById(11L, 8L)).thenReturn(0);
        DeckDataDeletionServiceImpl service = new DeckDataDeletionServiceImpl(
            cardProgressMapper,
            cardMediaMapper,
            cardMapper,
            deckSettingsMapper,
            practiceSessionStoreMapper,
            deckMapper,
            eventPublisher
        );

        AppException error = assertThrows(AppException.class,
            () -> service.deleteOwnedDeck(8L, 11L));

        assertEquals(ErrorCode.DECK_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(eventPublisher);
    }
}
