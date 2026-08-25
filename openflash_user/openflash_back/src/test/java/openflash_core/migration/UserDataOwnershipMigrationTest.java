package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserDataOwnershipMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V56__enforce_user_data_ownership.sql");

    @Test
    void migrationCleansOrphansBeforeAddingEveryCascadeOwnershipConstraint() throws Exception {
        String sql = normalize(Files.readString(MIGRATION));
        int firstConstraint = sql.indexOf("ADD CONSTRAINT");

        assertTrue(firstConstraint > 0);
        for (String cleanup : List.of(
                "DELETE cm FROM pw_card_media cm",
                "DELETE cp FROM pw_card_progress cp",
                "DELETE ds FROM pw_deck_settings ds",
                "DELETE das FROM pw_deck_ai_settings das",
                "DELETE tds FROM pw_tts_deck_settings tds",
                "DELETE mds FROM pw_mask_mode_deck_settings mds",
                "DELETE ps FROM pw_practice_session_store ps",
                "DELETE pi FROM pw_plugin_install pi",
                "DELETE c FROM pw_card c",
                "DELETE d FROM pw_deck d",
                "DELETE s FROM pw_user_settings s",
                "DELETE a FROM pw_user_ai_config a",
                "DELETE f FROM pw_user_feature_flag f",
                "DELETE up FROM pw_user_upload up")) {
            int cleanupIndex = sql.indexOf(cleanup);
            assertTrue(cleanupIndex >= 0, () -> "missing orphan cleanup: " + cleanup);
            assertTrue(cleanupIndex < firstConstraint, () -> "cleanup must precede constraints: " + cleanup);
        }

        for (String constraint : List.of(
                "ADD CONSTRAINT fk_deck_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_card_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_card_media_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_card_progress_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_card_progress_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_user_ai_config_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_user_feature_flag_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_user_upload_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_practice_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_plugin_install_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_plugin_install_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_deck_ai_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_tts_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_mask_mode_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE",
                "ADD CONSTRAINT fk_async_task_owner_user FOREIGN KEY (owner_user_id) REFERENCES pw_user(id) ON DELETE CASCADE")) {
            assertTrue(sql.contains(constraint), () -> "missing cascade FK: " + constraint);
        }
    }

    @Test
    void asyncOwnerIsNullableAndBackfillRequiresExistingUser() throws Exception {
        String sql = normalize(Files.readString(MIGRATION));

        assertTrue(sql.contains("ALTER TABLE pw_async_task ADD COLUMN owner_user_id BIGINT NULL AFTER id"));
        assertTrue(sql.contains("UPDATE pw_async_task t JOIN pw_user u ON u.id = CAST(COALESCE("));
        assertTrue(sql.contains("SET t.owner_user_id = u.id"));
        assertTrue(sql.contains("WHERE t.task_type IN ('AI_CACHE_BUILD', 'CARD_SIDE_COMPLETION') AND JSON_VALID(t.payload)"));
    }

    @Test
    void migrationDoesNotDeleteSharedCaches() throws Exception {
        String sql = normalize(Files.readString(MIGRATION)).toLowerCase();

        assertFalse(sql.contains("delete from pw_card_ai_cache"));
        assertFalse(sql.contains("delete from pw_tts_cache_meta"));
    }

    private static String normalize(String sql) {
        return sql.replace('`', ' ').replaceAll("\\s+", " ").trim();
    }
}
