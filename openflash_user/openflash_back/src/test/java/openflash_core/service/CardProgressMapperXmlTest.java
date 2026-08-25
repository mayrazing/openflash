package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CardProgressMapperXmlTest {

    /**
     * 验证今日已复习方向数查询按卡包过滤，防止 A 卡包完成后 B 卡包也被误判为额度耗尽。
     */
    @Test
    void countReviewedDirectionsTodayJoinsPwCardAndFiltersByDeckId() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardProgressMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("join pw_card"), "countReviewedDirectionsToday 须 join pw_card 才能按卡包隔离");
            assertTrue(xml.contains("deck_id = #{deckId}"), "须按 deck_id 过滤，否则跨卡包复习量互相干扰");
        }
    }
}
