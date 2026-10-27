package ict.china.workday.service;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * gov.cn 详情页 HTML 解析器：将「部分节假日安排的通知」页面内容解析为 HolidayRecord 集合。
 *
 * <p>解析规则（设计文档 §7），适配 gov.cn 真实正文格式：
 * <ul>
 *   <li>放假区间：`X月X日…至（X月）X日放假调休，共N天`（结束日期可省略月份的「至X日」形式，如
 *       「2月15日…至23日」）→ 区间内全部日期为 HOLIDAY。</li>
 *   <li>调休上班：`（X月）X日、…（X月）X日上班` → 句中每个日期为 WORKDAY；同一句中可并列多个上班日。</li>
 *   <li>按 holiday_date 去重；区间内的起始/结束日期也纳入区间处理。</li>
 * </ul>
 */
@Component
public class GovDetailParser {

    private static final Logger log = LoggerFactory.getLogger(GovDetailParser.class);

    /** 放假区间（结束可省略月份）：如「2月15日…至23日放假」或「2月15日至2月23日放假」 */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日.*?至\\s*(?:(\\d{1,2})\\s*月\\s*)?(\\d{1,2})\\s*日");
    /** 单个日期：X月X日 */
    private static final Pattern DATE = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    /** 句子分隔符 */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。；;\\n]");
    /** 节日小标题：`一、元旦：` / `二、春节：`（序号可选） */
    private static final Pattern FESTIVAL_SEG = Pattern.compile(
            "(?:[一二三四五六七八九十]+、)?(元旦|春节|清明节?|劳动节|端午节?|中秋节?|国庆节?)\\s*[：:]");

    private static final String SOURCE = "gov.cn";

    public List<HolidayRecord> parse(String html, int year) {
        List<HolidayRecord> records = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        if (html == null || html.isBlank()) {
            return records;
        }
        String text = toText(html);
        parseSegments(text, year, records, seen);
        records.sort((a, b) -> a.getHolidayDate().compareTo(b.getHolidayDate()));
        return records;
    }

    /**
     * 按节日小标题把正文切分为多个小节，每节用各自节日名解析。
     * 段间以「。；;\n」切句，放假句标 HOLIDAY、上班句标 WORKDAY。
     */
    private void parseSegments(String text, int year,
                               List<HolidayRecord> records, Set<LocalDate> seen) {
        Matcher fm = FESTIVAL_SEG.matcher(text);
        List<int[]> bounds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (fm.find()) {
            bounds.add(new int[]{fm.start(), fm.end()});
            names.add(fm.group(1));
        }
        if (bounds.isEmpty()) {
            // 无法按小标题分段（老格式），整段用兜底名称
            parseSegment(text, year, "节假日", records, seen);
            return;
        }
        for (int i = 0; i < bounds.size(); i++) {
            int from = bounds.get(i)[1];
            int to = (i + 1 < bounds.size()) ? bounds.get(i + 1)[0] : text.length();
            String segText = text.substring(from, Math.min(to, text.length()));
            parseSegment(segText, year, names.get(i), records, seen);
        }
    }

    private void parseSegment(String segText, int year, String festival,
                              List<HolidayRecord> records, Set<LocalDate> seen) {
        String[] sentences = SENTENCE_SPLIT.split(segText);
        for (String sentence : sentences) {
            if (sentence.contains("放假")) {
                collectHolidays(sentence, year, festival, records, seen);
            } else if (sentence.contains("上班")) {
                collectWorkdays(sentence, year, festival, records, seen);
            }
        }
    }

    private String toText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style").remove();
        // 优先限定到正文容器（UCAP-CONTENT 或 TRS 编辑器 div），避免把页头/页脚、
        // 成文日期、分享语等非正文文本一并纳入（否则正文末尾的落款「2025年11月X日」
        // 会被误解析为放假日期）。找不到时退回整页 body。
        org.jsoup.nodes.Element content = doc.selectFirst("#UCAP-CONTENT");
        if (content == null) {
            content = doc.selectFirst(".trs_editor_view");
        }
        org.jsoup.nodes.Element base = content != null ? content : doc.body();
        String txt = base == null ? doc.text() : base.text();
        return txt == null ? "" : txt;
    }

    /** 从「放假」句中提取区间（含省略月份的结束日期），全部标 HOLIDAY。 */
    private void collectHolidays(String sentence, int year, String festival,
                                 List<HolidayRecord> records, Set<LocalDate> seen) {
        Set<LocalDate> covered = new HashSet<>();
        Matcher rm = RANGE.matcher(sentence);
        while (rm.find()) {
            int sm = Integer.parseInt(rm.group(1));
            int sd = Integer.parseInt(rm.group(2));
            Integer em = rm.group(3) == null ? null : Integer.parseInt(rm.group(3));
            int ed = Integer.parseInt(rm.group(4));
            try {
                LocalDate start = LocalDate.of(year, sm, sd);
                // 若结束月份省略（多为同月内），沿用开始月份；否则用显式结束月
                LocalDate end = em == null
                        ? LocalDate.of(year, sm, ed)
                        : LocalDate.of(year, em, ed);
                if (end.isBefore(start)) {
                    log.warn("放假区间结束早于开始，跳过: {}", rm.group());
                    continue;
                }
                LocalDate cur = start;
                while (!cur.isAfter(end)) {
                    covered.add(cur);
                    addRecord(cur, festival, DayType.HOLIDAY, records, seen);
                    cur = cur.plusDays(1);
                }
            } catch (Exception e) {
                log.warn("忽略无法解析的放假区间: {}", rm.group());
            }
        }
        // 兜底：单日放假（未命中范围的日期也标 HOLIDAY）
        Matcher dm = DATE.matcher(sentence);
        while (dm.find()) {
            int month = Integer.parseInt(dm.group(1));
            int day = Integer.parseInt(dm.group(2));
            try {
                LocalDate d = LocalDate.of(year, month, day);
                if (!covered.contains(d)) {
                    addRecord(d, festival, DayType.HOLIDAY, records, seen);
                }
            } catch (Exception e) {
                log.warn("忽略无法解析的放假日期: {}", dm.group());
            }
        }
    }

    /** 从「上班」句中提取所有日期，全部标 WORKDAY（支持并列多个上班日）。 */
    private void collectWorkdays(String sentence, int year, String festival,
                                 List<HolidayRecord> records, Set<LocalDate> seen) {
        Matcher dm = DATE.matcher(sentence);
        while (dm.find()) {
            int month = Integer.parseInt(dm.group(1));
            int day = Integer.parseInt(dm.group(2));
            try {
                addRecord(LocalDate.of(year, month, day), festival + "调休", DayType.WORKDAY, records, seen);
            } catch (Exception e) {
                log.warn("忽略无法解析的调休上班日期: {}", dm.group());
            }
        }
    }

    private void addRecord(LocalDate date, String name, DayType type,
                           List<HolidayRecord> records, Set<LocalDate> seen) {
        if (date == null || !seen.add(date)) {
            return;
        }
        HolidayRecord r = new HolidayRecord(date, name, type, date.getYear(), SOURCE);
        r.setCreatedAt(LocalDateTime.now());
        records.add(r);
    }
}
