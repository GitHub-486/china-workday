package ict.china.workday.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ict.china.workday.client.GovCrawlClient;
import ict.china.workday.config.CrawlProperties;
import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.repository.CrawlUrlRepository;
import ict.china.workday.repository.HolidayRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
        return crawlOnce(true);
    }

    /**
     * @param truncate 是否先清空 holiday / crawl_url 两表再全量重新入库
     */
    public String crawlOnce(boolean truncate) {
        if (!crawlProperties.isEnabled()) {
            log.info("抓取已通过配置关闭");
            return "抓取已通过配置关闭";
        }

        log.info("===== 抓取任务开始 (truncate={}) =====", truncate);
        int nowYear = LocalDate.now().getYear();
        int minYear = nowYear - crawlProperties.getYearBackOffset();
        int maxYear = nowYear + crawlProperties.getYearForwardOffset();
        log.info("保留年份范围: [{} ~ {}] (backOffset={}, forwardOffset={})",
                minYear, maxYear,
                crawlProperties.getYearBackOffset(), crawlProperties.getYearForwardOffset());

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
        try {
            detailUrls = fetchNoticeUrlsFromSearch();
            log.info("搜索接口返回 {} 个通知 URL", detailUrls.size());
        } catch (Exception e) {
            log.warn("搜索接口不可用（{}），回退列表页抓取", e.getMessage());
            detailUrls = fetchNoticeUrlsFromList();
            log.info("列表页回退返回 {} 个通知 URL", detailUrls.size());
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
        log.info("汇总: 处理 {} 个, 跳过 {}(保留范围外) 个, 去重 {}(已抓取) 个, 失败 {} 个, 耗时 {} ms",
                processed, skipped, deduped, failed.size(), costMs);
        if (!failed.isEmpty()) {
            log.info("失败 URL 列表:");
            for (String u : failed) {
                log.info("  - {}", u);
            }
        }

        String summary = truncate
                ? "已清空并重新入库，本次处理 " + processed + " 个通知"
                : "本次新增/刷新通知 " + processed + " 个";
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
     * 从列表页 HTML 提取「部分节假日安排的通知」详情页 URL（回退通道）。
     */
    List<String> fetchNoticeUrlsFromList() {
        String listHtml = crawlClient.fetch(crawlProperties.getListUrl());
        Set<String> urls = new HashSet<>();
        Document doc = Jsoup.parse(listHtml);
        for (Element a : doc.select("a[href]")) {
            String title = a.text();
            if (!NOTICE_TITLE.matcher(title).matches()) {
                continue;
            }
            String href = a.absUrl("href");
            if (href.startsWith("http")) {
                urls.add(href);
            }
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
}
