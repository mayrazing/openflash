package openflash_core.service.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class CardSideANormalizer {

    private static final Pattern CONTINUOUS_WHITESPACE = Pattern.compile("\\s+");

    private CardSideANormalizer() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String collapsed = CONTINUOUS_WHITESPACE.matcher(nfkc.trim()).replaceAll(" ");
        return collapsed.toLowerCase(Locale.ROOT);
    }
}
