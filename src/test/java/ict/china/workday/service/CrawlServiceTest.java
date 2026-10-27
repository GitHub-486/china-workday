package ict.china.workday.service;

import ict.china.workday.config.CrawlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CrawlService 搜索结果提取逻辑测试（基于 gov.cn 官方搜索接口真实 JSON 结构）。
 */
class CrawlServiceTest {

    private final CrawlService service = new CrawlService(null, null, null, null, null);

    private String loadSample() throws Exception {
        org.springframework.core.io.Resource r = new ClassPathResource("search-result-sample.json");
        return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
    }

    @Test
    void extractNoticeUrls_filtersTitlesAndGathersBothCats() throws Exception {
        List<String> urls = service.extractNoticeUrls(loadSample());
        // gongwen 中匹配 1 条（2026 通知），命中 "部分节假日安排的通知"；另一条非通知被过滤
        // gongbao 中匹配 1 条（2025 通知）
        assertEquals(2, urls.size());
        assertTrue(urls.contains("https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"));
        assertTrue(urls.contains("https://www.gov.cn/gongbao/2024/issue_11726/202411/content_6989767.html"));
    }

    @Test
    void extractNoticeUrls_dedup() throws Exception {
        String json = "{\"searchVO\":{\"catMap\":{\"gongwen\":{\"listVO\":["
                + "{\"title\":\"关于2026年部分节假日安排的通知\",\"url\":\"http://www.gov.cn/a.htm\"},"
                + "{\"title\":\"关于2026年部分节假日安排的通知\",\"url\":\"http://www.gov.cn/a.htm\"}]}}}}";
        List<String> urls = service.extractNoticeUrls(json);
        assertEquals(1, urls.size());
    }

    @Test
    void extractNoticeUrls_invalidJson_returnsEmpty() {
        assertTrue(service.extractNoticeUrls("{not json").isEmpty());
    }

    @Test
    void inferYear_real2026Notice_returns2026NotPublishYear() throws Exception {
        // 2026 通知是 2025-11 发布，URL 含 202511（发布时间），但标题/正文指向 2026 年
        String url = "https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm";
        org.springframework.core.io.Resource r = new ClassPathResource("gov-notice-2026.html");
        String html = StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
        assertEquals(2026, service.inferYear(url, html));
    }

    @Test
    void inferYear_plainUrl_fallsBackToNowYearOrUrlYear() {
        // 无标题年份信息时，至少返回一个合法年份（2000-2100）
        int y = service.inferYear("https://www.gov.cn/zhengce/zhengceku/x.htm", "<html><body>无年份</body></html>");
        assertTrue(y >= 2000 && y <= 2100);
    }

    @Test
    void withinRetentionRange_onlyKeepsNearbyYears() {
        // 默认配置：当前年 ±1 年
        CrawlProperties props = new CrawlProperties();
        CrawlService s = new CrawlService(props, null, null, null, null);
        int now = LocalDate.now().getYear();
        assertTrue(s.withinRetentionRange(now));
        assertTrue(s.withinRetentionRange(now - 1));
        assertTrue(s.withinRetentionRange(now + 1));
        assertFalse(s.withinRetentionRange(now - 5));
        assertFalse(s.withinRetentionRange(now + 5));
    }
}
