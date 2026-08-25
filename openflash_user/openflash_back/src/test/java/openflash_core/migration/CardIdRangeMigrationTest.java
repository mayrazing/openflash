package openflash_core.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardIdRangeMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V49__limit_card_ids_to_fsrs_int.sql"
    );

    @Test
    void limitsCardAndCardReferencesToSignedInt() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V49 card-id range migration is missing");
        String sql = Files.readString(MIGRATION).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertTrue(sql.matches("(?s).*alter table pw_card .*modify column id int not null auto_increment.*"));
        assertTrue(sql.matches("(?s).*alter table pw_card_progress .*modify column card_id int not null.*"));
        assertTrue(sql.matches("(?s).*alter table pw_card_media .*modify column card_id int not null.*"));
        assertFalse(sql.contains("unsigned"), "java-fsrs accepts only signed 32-bit card ids");
    }
}
