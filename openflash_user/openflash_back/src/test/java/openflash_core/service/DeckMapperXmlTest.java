package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DeckMapperXmlTest {

    /**
     * 验证首页卡包列表直接返回卡片数量统计，避免前端为数字加载每个卡包的全量卡片。
     */
    @Test
    void findByUserIdReturnsHomeCardCounters() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/DeckMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("active_count"), "卡包列表须返回未掌握卡片数量");
            assertTrue(xml.contains("mastered_count"), "卡包列表须返回已掌握卡片数量");
            assertTrue(xml.contains("pw_card_progress"), "统计须读取学习进度表，保持会了入口口径一致");
            assertTrue(xml.contains("mastered_at is not null"), "已掌握统计须按 mastered_at 判断");
        }
    }
}
