package com.ict.workday.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ict.workday.client.GovCrawlClient;
import com.ict.workday.config.CrawlProperties;
import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import com.ict.workday.repository.CrawlUrlRepository;
import com.ict.workday.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.util.regex.Pattern;

/**
 * 抓取编排：发现「部分节假日安排的通知」详情页并解析入库；支持手动触发与启动抓取。
 *
 * <p>主通道使用 gov.cn 官方搜索数据接口（/search-gov/data）获取各年份通知详情 URL
 * （该接口返回 JSON，含 gongwen / gongbao 两类结果）；列表页抓取作为回退。
 * 详情页 HTML 交由 {@link GovDetailParser} 解析为节假日/调休记录。
 *
 * <p>幂等：详情 URL 去重（crawl_url 表）；holiday_date 唯一约束按日期合并。
 * 失败处理：单次抓取失败不影响已入库数据，仅记录日志，下轮重试。</p>
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private static final Pattern NOTICE_TITLE = Pattern.compile(".*部分节假日安排的通知.*");
    /** 用于从链接/文本推断年份，形如 2026 或「2026 年」 */
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    /** 标题年份：「关于XXXX年部分节假日安排的通知」 */
    private static final Pattern TITLE_YEAR = Pattern.compile("关于\\s*(20\\d{2})\\s*年");
    /** 正文安排年份：「XXXX年元旦、春节、…」 */
    private static final Pattern ARRANGE_YEAR = Pattern.compile("(20\\d{2})\\s*年\\s*元旦");

    private final CrawlProperties crawlProperties;
    private final GovCrawlClient crawlClient;
    private final GovDetailParser parser;
    private final HolidayRepository holidayRepository;
    private final CrawlUrlRepository crawlUrlRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrawlService(CrawlProperties crawlProperties,
                        GovCrawlClient crawlClient,
                        GovDetailParser parser,
                        HolidayRepository holidayRepository,
                        CrawlUrlRepository crawlUrlRepository) {
        this.crawlProperties = crawlProperties;
        this.crawlClient = crawlClient;
        this.parser = parser;
        this.holidayRepository = holidayRepository;
        this.crawlUrlRepository = crawlUrlRepository;
    }

    /**
     * 执行一次完整抓取：先从搜索接口发现通知详情 URL，解析未入库的通知。
     *
     * <p>手动触发默认先清空两表再全量入库（truncate=true）；启动/定时抓取
     * 传 truncate=false，不清空，按 URL 去重增量入库。</p>
     *
     * @return 汇总文案（本次新增/刷新记录数）
     */
    public String crawlOnce() {
        return crawlOnce(true, false);
    }

    /**
     * @param truncate 是否先清空两表
     * @param useFallback 是否强制走回退通道（跳过主搜索接口）
     */
    public String crawlOnce(boolean truncate, boolean useFallback) {
        if (!crawlProperties.isEnabled()) {
            log.info("抓取已通过配置关闭");
            return "抓取已通过配置关闭";
        }

        log.info("===== 抓取任务开始 (truncate={}, fallback={}) =====", truncate, useFallback);
        int nowYear = LocalDate.now().getYear();
        int minYear = nowYear - crawlProperties.getYearBackOffset();
        int maxYear = nowYear + crawlProperties.getYearForwardOffset();
        log.info("保留年份范围: [{} ~ {}]", minYear, maxYear);

        if (truncate) {
            holidayRepository.truncate();
            crawlUrlRepository.truncate();
            log.info("已清空 holiday 和 crawl_url 表");
        }

        List<String> detailUrls;
        int processed = 0;
        int skipped = 0;
        int deduped = 0;
        List<String> failed = new ArrayList<>();
        List<Integer> processedYears = new ArrayList<>();
        List<Integer> skippedYears = new ArrayList<>();

        if (useFallback) {
            log.info("强制走回退通道");
            detailUrls = fetchNoticeUrlsFromList();
            log.info("回退通道返回 {} 个通知 URL", detailUrls.size());
        } else {
            try {
                detailUrls = fetchNoticeUrlsFromSearch();
                log.info("搜索接口返回 {} 个通知 URL", detailUrls.size());
            } catch (Exception e) {
                log.warn("搜索接口不可用（{}），回退列表页抓取", e.getMessage());
                detailUrls = fetchNoticeUrlsFromList();
                log.info("回退通道返回 {} 个通知 URL", detailUrls.size());
            }
        }



        int total = detailUrls.size();
        log.info("本次共需处理 {} 个通知", total);
        long start = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            String url = detailUrls.get(i);
            log.info("---- 处理 [{}/{}] {}", i + 1, total, url);
            if (!truncate && crawlUrlRepository.exists(url)) {
                deduped++;
                log.info("  >> 已抓取过，按 URL 去重跳过");
                continue;
            }
            try {
                String html = crawlClient.fetch(url);
                log.info("  >> 抓取详情页成功，HTML {} 字节", html == null ? 0 : html.length());
                int year = inferYear(url, html);
                log.info("  >> 推断年份 = {}", year);
                if (!withinRetentionRange(year)) {
                    crawlUrlRepository.record(url, year);
                    skipped++;
                    skippedYears.add(year);
                    log.info("  >> 通知年份 {} 不在保留范围内，跳过", year);
                    continue;
                }
                List<HolidayRecord> records = parser.parse(html, year);
                int holiday = 0;
                int workday = 0;
                for (HolidayRecord r : records) {
                    if (r.getDayType() == DayType.WORKDAY) {
                        workday++;
                    } else {
                        holiday++;
                    }
                }
                holidayRepository.upsertAll(records);
                crawlUrlRepository.record(url, year);
                processed++;
                processedYears.add(year);
                log.info("  >> 解析并入库 {} 年通知: {} 条 (放假 {}, 调休上班 {})",
                        year, records.size(), holiday, workday);
            } catch (Exception e) {
                failed.add(url);
                log.warn("  >> 通知详情解析失败: {} - {}", url, e.getMessage());
                log.debug("  >> 异常堆栈", e);
            }
        }
        long costMs = System.currentTimeMillis() - start;

        log.info("===== 抓取任务结束 =====");
        String procYears = processedYears.isEmpty() ? "无" : formatYearRange(processedYears);
        String skipYears = skippedYears.isEmpty() ? "无" : formatYearRange(skippedYears);
        log.info("汇总: 入库 {} 个 (年份: {}), 跳过 {} 个 (年份: {}), 去重 {} 个, 失败 {} 个, 耗时 {} ms",
                processed, procYears, skipped, skipYears, deduped, failed.size(), costMs);
        if (!failed.isEmpty()) {
            log.info("失败 URL 列表:");
            for (String u : failed) {
                log.info("  - {}", u);
            }
        }

        String summary = truncate
                ? "已清空并重新入库，本次处理 " + processed + " 个通知"
                : "本次新增/刷新通知 " + processed + " 个";
        if (processed > 0) {
            String yrs = processedYears.stream().sorted().distinct().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
            summary += "（年份：" + yrs + "）";
        }
        if (skipped > 0) {
            summary += "，保留范围外跳过 " + skipped + " 个";
        }
        if (deduped > 0) {
            summary += "，已抓取去重 " + deduped + " 个";
        }
        if (!failed.isEmpty()) {
            summary += "，失败 " + failed.size() + " 个";
        }
        return summary;
    }

    /**
     * 通过官方搜索接口获取「部分节假日安排的通知」详情 URL 列表。
     */
    List<String> fetchNoticeUrlsFromSearch() {
        String keyword = URLEncoder.encode(crawlProperties.getSearchKeyword(), StandardCharsets.UTF_8);
        String url = crawlProperties.getSearchUrl()
                + "?t=zhengcelibrary&q=" + keyword
                + "&timetype=timeqb&mintime=&maxtime=&sort=score&sortType=1&searchfield=title"
                + "&pcodeJiguan=&childtype=&tsbq=&pubtimeyear=&puborg=&pcodeYear=&pcodeNum=&filetype="
                + "&p=1&n=" + crawlProperties.getSearchPageSize()
                + "&inpro=&bmfl=&dup=&orpro=&type=gwyzcwjk";

        String json = crawlClient.fetchJson(url);
        return extractNoticeUrls(json);
    }

    /**
     * 从搜索结果 JSON 提取「部分节假日安排的通知」详情 URL（gongwen + gongbao）。
     */
    List<String> extractNoticeUrls(String json) {
        Set<String> urls = new HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode catMap = root.path("searchVO").path("catMap");
            collectUrls(catMap.path("gongwen").path("listVO"), urls);
            collectUrls(catMap.path("gongbao").path("listVO"), urls);
        } catch (Exception e) {
            log.warn("搜索结果解析失败: {}", e.getMessage());
        }
        return new ArrayList<>(urls);
    }

    private void collectUrls(JsonNode list, Set<String> urls) {
        if (list == null || !list.isArray()) {
            return;
        }
        for (JsonNode item : list) {
            String title = item.path("title").asText("");
            if (!NOTICE_TITLE.matcher(stripEm(title)).matches()) {
                continue;
            }
            String u = item.path("url").asText("");
            if (u.startsWith("http")) {
                urls.add(u);
            }
        }
    }

    private String stripEm(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "");
    }

    /**
     * 从 gov.cn athena JSON 搜索接口获取「部分节假日安排的通知」详情 URL（回退通道）。
     *
     * <p>两步流程：
     * 1. GET 请求获取动态 codeCode（认证码，每次加载页面变化）
     * 2. 用 codeCode POST 搜索，获取节假日通知详情 URL 列表
     */
    List<String> fetchNoticeUrlsFromList() {
        // 1) 获取动态 codeCode
        String codeUrl = "https://sousuoht.www.gov.cn/athena/forward/DA2FE8C6CAD0EEBC5F97F7E3F3633A7188DAA40373EEA0E4024A081201F4D546?thirdPartyName=hycloud&thirdPartyTenantId=8";
        String codeJson = crawlClient.fetchAthenaGet(codeUrl);
        String codeCode = "";
        try {
            JsonNode codeRoot = objectMapper.readTree(codeJson);
            if (codeRoot.path("resultCode").path("code").asInt() == 200) {
                codeCode = codeRoot.path("result").path("data").asText("");
            }
        } catch (Exception e) {
            log.warn("获取 athena codeCode 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
        if (codeCode.isEmpty()) {
            log.warn("athena codeCode 为空，跳过搜索");
            return new ArrayList<>();
        }

        // 2) 前置：分类统计请求（初始化服务端会话状态，否则搜索返回空）
        String catUrl = "https://sousuoht.www.gov.cn/athena/forward/486B5ABFBAD0FF5743F5E82E007EF04D96B446B3BB6FEC0CE6E14915E8C3CDE6";
        String catBody1 = "{\"code\":\"" + codeCode + "\",\"thirdPartyCode\":\"thirdparty_code_107\",\"classInfos\":[{\"classInfoId\":1100,\"childrenInfoIds\":[1108,1107,1106,1105,1104,1103,1102,7547,7548,7549,1101,1100]},{\"classInfoId\":1077,\"childrenInfoIds\":[1078,1165,1166,1167,1168,1169,1170,1171,1172,1173,1174,1079,2152,2153,2154,2155,2156,2157,2158,2159,2160,1080,2161,2162,2163,2164,2165,2166,2167,1081,2168,2169,2170,2171,2172,2173,2174,2175,2176,1082,2177,2178,2179,2180,2181,2182,2183,2184,1083,2185,2186,2187,2188,1084,2189,2190,2191,2192,2193,2194,2195,2196,2197,2198,2199,2200,1085,2201,2202,2203,2204,2205,2206,1086,2207,2208,2209,2210,2211,2212,1087,2213,2214,2216,2217,2218,2219,1088,2220,2221,2222,2223,1089,2224,2225,2226,2227,2228,1090,2229,2230,2231,2232,1091,2233,2234,2235,1092,2236,2238,2240,2241,2242,2243,2244,1093,2245,2246,2247,2248,1094,2249,2250,2251,2252,2253,2254,2255,2919,2920,2921,1096,2262,2263,2264,1097,2265,2266,2267,1098,2268,2270,1099,1077]}],\"childrenInfoIds\":[],\"thirdPartyTableId\":30,\"searchFields\":[{\"fieldName\":\"maintitle\",\"searchWord\":\"部分节假日安排的通知\"}]}";
        String catBody2 = "{\"code\":\"" + codeCode + "\",\"thirdPartyCode\":\"thirdparty_code_107\",\"classInfos\":[{\"classInfoId\":1108,\"childrenInfoIds\":[1108]},{\"classInfoId\":1107,\"childrenInfoIds\":[1107]},{\"classInfoId\":1106,\"childrenInfoIds\":[1106]},{\"classInfoId\":1105,\"childrenInfoIds\":[1105]},{\"classInfoId\":1104,\"childrenInfoIds\":[1104]},{\"classInfoId\":1103,\"childrenInfoIds\":[1103]},{\"classInfoId\":1102,\"childrenInfoIds\":[1102]},{\"classInfoId\":7547,\"childrenInfoIds\":[7547]},{\"classInfoId\":7548,\"childrenInfoIds\":[7548]},{\"classInfoId\":7549,\"childrenInfoIds\":[7549]},{\"classInfoId\":1101,\"childrenInfoIds\":[1101]}],\"childrenInfoIds\":[],\"thirdPartyTableId\":30,\"searchFields\":[{\"fieldName\":\"maintitle\",\"searchWord\":\"部分节假日安排的通知\"}]}";
        try {
            crawlClient.fetchAthenaPost(catUrl, catBody1);
            crawlClient.fetchAthenaPost(catUrl, catBody2);
        } catch (Exception e) {
            log.warn("分类统计请求失败（不影响后续搜索）: {}", e.getMessage());
        }

        // 3) 搜索节假日通知
        String searchUrl = crawlProperties.getListUrl();
        String searchBody = "{\"code\":\"" + codeCode + "\",\"thirdPartyCode\":\"thirdparty_code_107\",\"thirdPartyTableId\":30,"
                + "\"resultFields\":[\"pub_url\",\"maintitle\",\"fwzh\",\"cwrq\",\"publish_time\"],\"trackTotalHits\":\"true\","
                + "\"searchFields\":[{\"fieldName\":\"maintitle\",\"searchWord\":\"部分节假日安排的通知\"}],\"isPreciseSearch\":0,"
                + "\"sorts\":[{\"sortField\":\"publish_time\",\"sortOrder\":\"DESC\"}],"
                + "\"childrenInfoIds\":[[1108,1107,1106,1105,1104,1103,1102,7547,7548,7549,1101,\"1100\"]],"
                + "\"pageSize\":20,\"pageNo\":1}";
        String json = crawlClient.fetchAthenaPost(searchUrl, searchBody);
        Set<String> urls = new HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode list = root.path("result").path("data").path("list");
            if (list == null || !list.isArray()) {
                log.warn("列表 JSON 无 list 数组");
                return new ArrayList<>();
            }
            for (JsonNode item : list) {
                String title = item.path("maintitle").asText("");
                if (!NOTICE_TITLE.matcher(stripEm(title)).matches()) {
                    continue;
                }
                String url = item.path("pub_url").asText("");
                if (url.startsWith("http")) {
                    urls.add(url);
                }
            }
        } catch (Exception e) {
            log.warn("列表 JSON 解析失败: {}", e.getMessage());
        }
        return new ArrayList<>(urls);
    }

    /**
     * 推断通知所属年份。优先从标题提取「关于XXXX年」（标题中的年份才是安排年份，
     * 而非 URL 中的发布时间），随后用 title 泛化匹配、URL，最后兜底当前年份。
     */
    int inferYear(String url, String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style").remove();
        String title = doc.title() == null ? "" : doc.title();
        String text = title + " "
                + (doc.body() == null ? "" : doc.body().text());

        // 1) 标题中的「关于XXXX年部分节假日安排」（最准确）
        Matcher tm = TITLE_YEAR.matcher(title);
        if (tm.find()) {
            int y = Integer.parseInt(tm.group(1));
            if (y >= 2000 && y <= 2100) {
                return y;
            }
        }
        // 2) 正文中「XXXX年元旦、春节…」等安排年份
        Matcher em = ARRANGE_YEAR.matcher(text);
        if (em.find()) {
            int y = Integer.parseInt(em.group(1));
            if (y >= 2000 && y <= 2100) {
                return y;
            }
        }
        // 3) URL 兜底（旧格式 URL 可能不含安排年份或含发布年份）
        Matcher um = YEAR_PATTERN.matcher(url);
        if (um.find()) {
            int y = Integer.parseInt(um.group(1));
            if (y >= 2000 && y <= 2100) {
                return y;
            }
        }
        return LocalDate.now().getYear();
    }

    /**
     * 判断年份是否在保留范围内：保留 [当前年-backOffset, 当前年+forwardOffset]。
     */
    boolean withinRetentionRange(int year) {
        int now = LocalDate.now().getYear();
        int min = now - crawlProperties.getYearBackOffset();
        int max = now + crawlProperties.getYearForwardOffset();
        return year >= min && year <= max;
    }

    /** 将年份列表格式化为区间表示，如 "2007~2024"。 */
    private String formatYearRange(List<Integer> years) {
        if (years.isEmpty()) return "无";
        java.util.Collections.sort(years);
        int start = years.get(0);
        int end = years.get(years.size() - 1);
        if (start == end) return String.valueOf(start);
        return start + "~" + end;
    }
}
