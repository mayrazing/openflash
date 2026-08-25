package openflash_core.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class CardControllerAiRouteTest {

    @Test
    void cardAiMarkdownRouteIsNotExposedAsProductPath() {
        boolean exposesLegacyRoute = Arrays.stream(CardController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(GetMapping.class))
            .filter(mapping -> mapping != null)
            .flatMap(mapping -> Arrays.stream(mapping.value()))
            .anyMatch("/cards/{cardId}/ai-markdown"::equals);

        assertFalse(exposesLegacyRoute);
    }

    @Test
    void cardControllerDoesNotExposeAiCacheStatusRoute() {
        boolean exposesAiCacheRoute = Arrays.stream(CardController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(GetMapping.class))
            .filter(mapping -> mapping != null)
            .flatMap(mapping -> Arrays.stream(mapping.value()))
            .anyMatch("/cards/{cardId}/ai-cache-status"::equals);
        assertFalse(exposesAiCacheRoute);
    }
}
