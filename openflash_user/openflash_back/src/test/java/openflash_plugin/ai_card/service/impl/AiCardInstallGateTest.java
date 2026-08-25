package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import openflash_core.entity.Card;
import openflash_core.mapper.CardMapper;
import openflash_core.service.PluginInstallService;
import org.junit.jupiter.api.Test;

class AiCardInstallGateTest {

    /** 构造带 id 与 deckId 的卡片。 */
    private static Card card(long id, long deckId) {
        Card c = new Card();
        c.setId(id);
        c.setDeckId(deckId);
        return c;
    }

    @Test
    void keepsCardsWhoseDeckHasAiCardInstalled() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        when(cardMapper.findByIds(List.of(10L))).thenReturn(List.of(card(10L, 100L)));
        when(installService.isInstalledOnDeck(100L, "ai-card")).thenReturn(true);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        assertEquals(List.of(10L), gate.retainInstalledDeckCards(List.of(10L)));
    }

    @Test
    void dropsCardsWhoseDeckHasNoAiCardInstalled() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        when(cardMapper.findByIds(List.of(10L))).thenReturn(List.of(card(10L, 100L)));
        when(installService.isInstalledOnDeck(100L, "ai-card")).thenReturn(false);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        assertEquals(List.of(), gate.retainInstalledDeckCards(List.of(10L)));
    }

    @Test
    void keepsOnlyInstalledDeckCardsAcrossMultipleDecks() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        when(cardMapper.findByIds(List.of(10L, 11L, 20L)))
            .thenReturn(List.of(card(10L, 100L), card(11L, 100L), card(20L, 200L)));
        when(installService.isInstalledOnDeck(100L, "ai-card")).thenReturn(true);
        when(installService.isInstalledOnDeck(200L, "ai-card")).thenReturn(false);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        assertEquals(List.of(10L, 11L), gate.retainInstalledDeckCards(List.of(10L, 11L, 20L)));
    }

    @Test
    void queriesInstallStateOncePerDeck() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        when(cardMapper.findByIds(List.of(10L, 11L)))
            .thenReturn(List.of(card(10L, 100L), card(11L, 100L)));
        when(installService.isInstalledOnDeck(100L, "ai-card")).thenReturn(true);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        gate.retainInstalledDeckCards(List.of(10L, 11L));

        verify(installService, times(1)).isInstalledOnDeck(eq(100L), eq("ai-card"));
    }

    @Test
    void returnsEmptyForEmptyInput() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        assertEquals(List.of(), gate.retainInstalledDeckCards(List.of()));
    }

    @Test
    void dropsCardIdWithNoMatchingCardRow() {
        CardMapper cardMapper = mock(CardMapper.class);
        PluginInstallService installService = mock(PluginInstallService.class);
        when(cardMapper.findByIds(List.of(10L, 99L))).thenReturn(List.of(card(10L, 100L)));
        when(installService.isInstalledOnDeck(100L, "ai-card")).thenReturn(true);
        AiCardInstallGate gate = new AiCardInstallGate(cardMapper, installService);

        assertEquals(List.of(10L), gate.retainInstalledDeckCards(List.of(10L, 99L)));
    }
}
