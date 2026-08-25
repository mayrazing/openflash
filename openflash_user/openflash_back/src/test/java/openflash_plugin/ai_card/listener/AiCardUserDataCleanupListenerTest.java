package openflash_plugin.ai_card.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import openflash_core.spi.UserDeletedEvent;
import openflash_plugin.ai_card.mapper.AiCardUserTaskCleanupMapper;
import org.junit.jupiter.api.Test;

class AiCardUserDataCleanupListenerTest {

    @Test
    void deletesPluginTasksForDeletedUser() {
        AiCardUserTaskCleanupMapper mapper = mock(AiCardUserTaskCleanupMapper.class);
        AiCardUserDataCleanupListener listener = new AiCardUserDataCleanupListener(mapper);

        listener.onUserDeleted(new UserDeletedEvent(8L));

        verify(mapper).deleteByUserId(8L);
    }

    @Test
    void mapperTargetsOnlyKnownAiTaskTypesAndUserPaths() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/openflash_plugin/ai_card/mapper/AiCardUserTaskCleanupMapper.xml"));

        assertTrue(sql.contains("task_type IN ('AI_CACHE_BUILD', 'CARD_SIDE_COMPLETION')"));
        assertTrue(sql.contains("payload IS JSON"));
        assertTrue(sql.contains("payload::jsonb"));
        assertTrue(sql.contains("-&gt;&gt; 'userId'"));
        assertTrue(sql.contains("#&gt;&gt; '{build,userId}'"));
        assertTrue(sql.contains("#&gt;&gt; '{notificationTarget,userId}'"));
        assertFalse(sql.contains("pw_card_ai_cache"));
        assertFalse(sql.contains("pw_tts_cache_meta"));
    }
}
