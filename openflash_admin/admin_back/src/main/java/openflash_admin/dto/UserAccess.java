package openflash_admin.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record UserAccess(
    Map<String, Boolean> cliAccess,
    Map<String, Boolean> offeringAccess
) {
    public UserAccess {
        cliAccess = immutable(cliAccess);
        offeringAccess = immutable(offeringAccess);
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
