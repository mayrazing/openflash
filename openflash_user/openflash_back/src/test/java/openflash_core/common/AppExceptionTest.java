package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AppExceptionTest {

    @Test
    void appExceptionCarriesErrorCode() {
        AppException ex = new AppException(ErrorCode.CARD_ALREADY_EXISTS);
        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void appExceptionValueMatchesErrorCode() {
        AppException ex = new AppException(ErrorCode.UNAUTHORIZED);
        assertEquals(40101, ex.getErrorCode().value());
    }
}
