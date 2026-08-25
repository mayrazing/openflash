package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CardMapperXmlTest {

    @Test
    void learningStatsCountsMasteredByMasteredAtAndExcludesGraduatedFromReviewDue() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("cp.mastered_at is not null"));
            assertTrue(xml.contains("cp.state not in ('new', 'mastered', 'graduated')"));
            assertFalse(xml.contains("card_state = 'mastered'"));
        }
    }

    @Test
    void learningStatsExposeCompletedTodayCardCounts() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("today_completed_new"));
            assertTrue(xml.contains("today_completed_review"));
            assertTrue(xml.contains("first_learned_date = #{today}"));
            assertTrue(xml.contains("last_review_date = #{today}"));
            assertFalse(xml.contains("today_studied_new"));
        }
    }

    @Test
    void pageFiltersUseMasteredAtAndGraduatedState() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("state != null and state == 'graduated'"));
            assertTrue(xml.contains("count(distinct case when mastered_at is not null then direction end)"));
            assertTrue(xml.contains("state = 'graduated'"));
            assertTrue(xml.contains("count(distinct case when state = 'graduated' then direction end)"));
            assertFalse(xml.contains("state = 'mastered') = 2"));
        }
    }

    /**
     * 验证新卡筛选不会把已经进入会了收集本的卡片继续展示出来。
     */
    @Test
    void newStateFilterExcludesMasteredProgressRows() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("state = 'new' and mastered_at is null"));
        }
    }

    /**
     * 分页列表只能按白名单切换创建时间排序。
     */
    @Test
    void pageSortUsesCreatedAtWhitelistBranches() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/CardMapper.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("sort == 'created_asc'"));
            assertTrue(xml.contains("order by c.created_at asc, c.id asc"));
            assertTrue(xml.contains("order by c.created_at desc, c.id desc"));
            assertFalse(xml.contains("${sort}"));
        }
    }

}
