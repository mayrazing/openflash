package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import openflash_plugin.ai_card.dto.AiCacheStatusResponse;

class CardAiServiceImplTest {

    @Test
    void checkAiCacheStatusDelegatesToResolver() {
        CardAiExplanationResolver resolver = mock(CardAiExplanationResolver.class);
        AiCacheStatusResponse expected = AiCacheStatusResponse.queued();
        when(resolver.resolveOrQueue(10L, "B")).thenReturn(expected);
        CardAiServiceImpl service = new CardAiServiceImpl(resolver);

        AiCacheStatusResponse response = service.checkAiCacheStatus(10L, "B");

        assertEquals(expected, response);
        verify(resolver).resolveOrQueue(10L, "B");
    }
}
