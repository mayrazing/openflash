package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsyncTaskMapperXmlTest {

    @Test
    void upsertUsesCallerProvidedFailedReschedulePolicy() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/AsyncTaskMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("#{rescheduleFailed} = true"));
        }
    }
}
