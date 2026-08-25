package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CardSideANormalizerTest {

    @Test
    void trimsAndIgnoresCase() {
        assertEquals("apple", CardSideANormalizer.normalize(" Apple "));
        assertEquals("apple", CardSideANormalizer.normalize("apple"));
    }

    @Test
    void collapsesContinuousWhitespace() {
        assertEquals("hello world", CardSideANormalizer.normalize("hello   world"));
        assertEquals("hello world", CardSideANormalizer.normalize("hello\t\nworld"));
    }

    @Test
    void appliesNfkcNormalization() {
        assertEquals("apple", CardSideANormalizer.normalize("Ａｐｐｌｅ"));
    }

    @Test
    void doesNotDropPunctuationOrStemWords() {
        assertNotEquals(CardSideANormalizer.normalize("apple"), CardSideANormalizer.normalize("apple."));
        assertNotEquals(CardSideANormalizer.normalize("run"), CardSideANormalizer.normalize("running"));
    }

    @Test
    void nullNormalizesToEmptyString() {
        assertEquals("", CardSideANormalizer.normalize(null));
    }
}
