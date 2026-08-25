package openflash_core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppLogTest {

    @Mock
    private Logger log;

    @Test
    void errorIncludesCodeNameInFormat() {
        AppLog.error(log, ErrorCode.GENERIC_ERROR, "something went wrong");
        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(log).error(fmtCaptor.capture(), any(Object[].class));
        assertTrue(fmtCaptor.getValue().contains("GENERIC_ERROR"),
            "format should contain code name");
    }

    @Test
    void errorIncludesCodeValueInFormat() {
        AppLog.error(log, ErrorCode.GENERIC_ERROR, "something went wrong");
        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(log).error(fmtCaptor.capture(), any(Object[].class));
        assertTrue(fmtCaptor.getValue().contains("50000"),
            "format should contain code value 50000");
    }

    @Test
    void warnIncludesCodeNameInFormat() {
        AppLog.warn(log, ErrorCode.UPLOAD_FILE_DELETE_FAILED, "file delete failed");
        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(log).warn(fmtCaptor.capture(), any(Object[].class));
        assertTrue(fmtCaptor.getValue().contains("UPLOAD_FILE_DELETE_FAILED"),
            "format should contain code name");
    }

    @Test
    void errorPreservesOriginalMessageInFormat() {
        AppLog.error(log, ErrorCode.CARD_NOT_FOUND, "card {} not found");
        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(log).error(fmtCaptor.capture(), any(Object[].class));
        assertTrue(fmtCaptor.getValue().contains("card {} not found"),
            "format should contain original message");
    }

    @Test
    void errorPassesThroughExtraArgs() {
        RuntimeException ex = new RuntimeException("cause");
        AppLog.error(log, ErrorCode.GENERIC_ERROR, "failed", ex);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(log).error(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        boolean hasException = false;
        for (Object a : args) {
            if (ex.equals(a)) { hasException = true; break; }
        }
        assertTrue(hasException, "extra args should be forwarded");
    }
}
