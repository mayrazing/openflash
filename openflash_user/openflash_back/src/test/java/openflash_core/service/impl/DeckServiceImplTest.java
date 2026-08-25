package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import org.mockito.InOrder;
import openflash_core.entity.CardMedia;
import openflash_core.entity.Deck;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.service.CurrentUserService;

class DeckServiceImplTest {

    /** 验证新增卡包时，插件市场里两个默认插件已经安装到这个卡包。 */
    @Test
    void createDeckInstallsDefaultPlugins() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        PluginInstallMapper pluginInstallMapper = mock(PluginInstallMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(7L);
        doAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            deck.setId(11L);
            return 1;
        }).when(deckMapper).insert(any(Deck.class));
        Deck savedDeck = new Deck();
        savedDeck.setId(11L);
        savedDeck.setUserId(7L);
        savedDeck.setName("默认卡包");
        when(deckMapper.findByIdAndUserId(11L, 7L)).thenReturn(savedDeck);
        DeckServiceImpl service = new DeckServiceImpl(
            currentUserService,
            deckMapper,
            mock(CardMediaMapper.class),
            deckSettingsMapper,
            pluginInstallMapper,
            mock(UploadFileDeleter.class),
            mock(DeckDataDeletionServiceImpl.class)
        );

        Deck result = service.createDeck(" 默认卡包 ");

        assertEquals(11L, result.getId());
        verify(pluginInstallMapper).insert(7L, 11L, "tts");
        verify(pluginInstallMapper).insert(7L, 11L, "ai-card");
        ArgumentCaptor<Deck> deckCaptor = ArgumentCaptor.forClass(Deck.class);
        verify(deckMapper).insert(deckCaptor.capture());
        assertEquals("默认卡包", deckCaptor.getValue().getName());
    }

    @Test
    void deleteDeckCapturesMediaThenDelegatesDbDeleteBeforeBestEffortFileDelete() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        UploadFileDeleter uploadFileDeleter = mock(UploadFileDeleter.class);
        DeckDataDeletionServiceImpl dataDeletionService = mock(DeckDataDeletionServiceImpl.class);
        when(currentUserService.getCurrentUserId()).thenReturn(8L);
        Deck deck = new Deck();
        deck.setId(11L);
        deck.setUserId(8L);
        when(deckMapper.findByIdAndUserId(11L, 8L)).thenReturn(deck);
        List<CardMedia> media = List.of(new CardMedia());
        when(cardMediaMapper.findByDeckId(11L)).thenReturn(media);
        DeckServiceImpl service = new DeckServiceImpl(
            currentUserService,
            deckMapper,
            cardMediaMapper,
            mock(DeckSettingsMapper.class),
            mock(PluginInstallMapper.class),
            uploadFileDeleter,
            dataDeletionService
        );

        service.deleteDeck(11L);

        InOrder order = inOrder(deckMapper, cardMediaMapper, dataDeletionService, uploadFileDeleter);
        order.verify(deckMapper).findByIdAndUserId(11L, 8L);
        order.verify(cardMediaMapper).findByDeckId(11L);
        order.verify(dataDeletionService).deleteOwnedDeck(8L, 11L);
        order.verify(uploadFileDeleter).delete(media);
    }
}
