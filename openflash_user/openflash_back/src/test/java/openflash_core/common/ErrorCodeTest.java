package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void cardAlreadyExistsHasCorrectValue() {
        assertEquals(40010, ErrorCode.CARD_ALREADY_EXISTS.value());
    }

    @Test
    void unauthorizedHasCorrectValue() {
        assertEquals(40101, ErrorCode.UNAUTHORIZED.value());
    }

    @Test
    void genericErrorHasCorrectValue() {
        assertEquals(50000, ErrorCode.GENERIC_ERROR.value());
    }

    @Test
    void uploadFileDeleteFailedHasCorrectValue() {
        assertEquals(50008, ErrorCode.UPLOAD_FILE_DELETE_FAILED.value());
    }

    @Test
    void unsupportedLanguageHasCorrectValue() {
        assertEquals(40033, ErrorCode.UNSUPPORTED_LANGUAGE.value());
    }
}
