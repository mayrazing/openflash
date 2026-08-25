package openflash_plugin.ai_card.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.spi.CardChangeEvent;

class AiCardChangeContributorTest {

    @Test
    void createdEventTriggersAiCacheAndSideCompletion() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L, 11L), CardChangeEvent.Kind.CREATED));

        verify(aiProducer).triggerCardsAfterCommit(List.of(10L, 11L), 7L);
        verify(sideProducer).triggerCardsAfterCommit(List.of(10L, 11L), 7L);
    }

    @Test
    void importedEventTriggersAiCacheAndSideCompletion() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(20L), CardChangeEvent.Kind.IMPORTED));

        verify(aiProducer).triggerCardsAfterCommit(List.of(20L), 7L);
        verify(sideProducer).triggerCardsAfterCommit(List.of(20L), 7L);
    }

    @Test
    void updatedEventTriggersAiCacheAndSideCompletion() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(30L), CardChangeEvent.Kind.UPDATED));

        verify(aiProducer).triggerCardsAfterCommit(List.of(30L), 7L);
        verify(sideProducer).triggerCardsAfterCommit(List.of(30L), 7L);
    }

    @Test
    void movedEventSkipsAiCacheAndSideCompletion() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.moved(7L, List.of(30L), 1L, 2L));

        verify(aiProducer, never()).triggerCardsAfterCommit(any(), anyLong());
        verify(sideProducer, never()).triggerCardsAfterCommit(any(), anyLong());
    }

    @Test
    void emptyCardIdsAreSkipped() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(), CardChangeEvent.Kind.CREATED));

        verify(aiProducer, never()).triggerCardsAfterCommit(any(), anyLong());
        verify(sideProducer, never()).triggerCardsAfterCommit(any(), anyLong());
    }

    @Test
    void skipsAllAiWorkWhenAiCardIsOff() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(false, true),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L), CardChangeEvent.Kind.CREATED));

        verify(aiProducer, never()).triggerCardsAfterCommit(any(), anyLong());
        verify(sideProducer, never()).triggerCardsAfterCommit(any(), anyLong());
    }

    @Test
    void skipsSideCompletionWhenSubfeatureOffButTriggersCache() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, false),
                passAllGate());

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L), CardChangeEvent.Kind.CREATED));

        verify(aiProducer).triggerCardsAfterCommit(List.of(10L), 7L);
        verify(sideProducer, never()).triggerCardsAfterCommit(any(), anyLong());
    }

    /** 创建指定插件开关状态的 guard。 */
    private static AiCardFeatureGuard guard(boolean aiCardEnabled, boolean sideCompletionEnabled) {
        AiCardFeatureGuard guard = mock(AiCardFeatureGuard.class);
        when(guard.isAiCardEnabled()).thenReturn(aiCardEnabled);
        when(guard.isSideCompletionEnabled()).thenReturn(sideCompletionEnabled);
        return guard;
    }

    /** 构造放行全部卡片的 gate（id 原样返回），隔离门控、复用既有断言。 */
    private static AiCardInstallGate passAllGate() {
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        when(gate.retainInstalledDeckCards(any())).thenAnswer(inv -> {
            java.util.Collection<Long> in = inv.getArgument(0);
            return List.copyOf(in);
        });
        return gate;
    }

    @Test
    void skipsAllAiWorkWhenGateDropsEveryCard() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        when(gate.retainInstalledDeckCards(any())).thenReturn(List.of());
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                gate);

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L, 11L), CardChangeEvent.Kind.CREATED));

        verify(aiProducer, never()).triggerCardsAfterCommit(any(), anyLong());
        verify(sideProducer, never()).triggerCardsAfterCommit(any(), anyLong());
    }

    @Test
    void triggersProducersWithGateFilteredSubset() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        when(gate.retainInstalledDeckCards(List.of(10L, 11L, 20L))).thenReturn(List.of(10L, 11L));
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(true, true),
                gate);

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L, 11L, 20L), CardChangeEvent.Kind.CREATED));

        verify(aiProducer).triggerCardsAfterCommit(List.of(10L, 11L), 7L);
        verify(sideProducer).triggerCardsAfterCommit(List.of(10L, 11L), 7L);
    }

    @Test
    void doesNotCallGateWhenAiCardGloballyOff() {
        CardAiCacheTaskProducer aiProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideProducer = mock(CardSideCompletionTaskProducer.class);
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        AiCardChangeContributor contributor = new AiCardChangeContributor(aiProducer, sideProducer, guard(false, true),
                gate);

        contributor.afterCardsChanged(CardChangeEvent.of(7L, List.of(10L), CardChangeEvent.Kind.CREATED));

        verify(gate, never()).retainInstalledDeckCards(any());
    }
}
