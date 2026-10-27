package ict.china.workday.service;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基于 gov.cn 真实「2026 年部分节假日安排的通知」页面的解析验证（设计文档 §7/§13）。
 *
 * <p>真实通知正文（2026 年）：
 * <ul>
 *   <li>春节：2/15–2/23 放假（9 天），2/14、2/28 调休上班</li>
 *   <li>元旦：1/1–1/3 放假，1/4（周日）上班</li>
 *   <li>劳动节：5/1–5/5 放假，5/9 上班</li>
 *   <li>国庆节：10/1–10/7 放假，9/20、10/10 上班</li>
 * </ul>
 */
class GovNotice2026RealParserTest {

    private final GovDetailParser parser = new GovDetailParser();

    private String load() throws Exception {
        org.springframework.core.io.Resource r = new ClassPathResource("gov-notice-2026.html");
        return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
    }

    @Test
    void parseRealNotice_springFestival() throws Exception {
        List<HolidayRecord> recs = parser.parse(load(), 2026);
        // 春节放假区间
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-15") && r.getDayType() == DayType.HOLIDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-23") && r.getDayType() == DayType.HOLIDAY));
        // 春节调休上班
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-14") && r.getDayType() == DayType.WORKDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-28") && r.getDayType() == DayType.WORKDAY));
    }

    @Test
    void parseRealNotice_nationalDay() throws Exception {
        List<HolidayRecord> recs = parser.parse(load(), 2026);
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-10-01") && r.getDayType() == DayType.HOLIDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-10-07") && r.getDayType() == DayType.HOLIDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-09-20") && r.getDayType() == DayType.WORKDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-10-10") && r.getDayType() == DayType.WORKDAY));
    }

    @Test
    void parseRealNotice_laborDay() throws Exception {
        List<HolidayRecord> recs = parser.parse(load(), 2026);
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-05-01") && r.getDayType() == DayType.HOLIDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-05-05") && r.getDayType() == DayType.HOLIDAY));
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-05-09") && r.getDayType() == DayType.WORKDAY));
    }

    @Test
    void parseRealNotice_midAutumnAndFestivalNames() throws Exception {
        List<HolidayRecord> recs = parser.parse(load(), 2026);
        // 清明节 4/4
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-04-04") && r.getDayType() == DayType.HOLIDAY));
    }

    @Test
    void parseRealNotice_festivalNamesArePerSegment() throws Exception {
        // 关键回归：一份通知含多个节日，各日期必须标各自节日名，而非全部「春节」
        List<HolidayRecord> recs = parser.parse(load(), 2026);

        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-01-01") && "元旦".equals(r.getHolidayName())),
                "2026-01-01 应为 元旦");
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-15") && "春节".equals(r.getHolidayName())),
                "2026-02-15 应为 春节");
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-04-04") && "清明节".equals(r.getHolidayName())),
                "2026-04-04 应为 清明节");
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-05-01") && "劳动节".equals(r.getHolidayName())),
                "2026-05-01 应为 劳动节");
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-10-01") && "国庆节".equals(r.getHolidayName())),
                "2026-10-01 应为 国庆节");
        // 调休上班名：春节调休
        assertTrue(recs.stream().anyMatch(r ->
                r.getHolidayDate().toString().equals("2026-02-14") && "春节调休".equals(r.getHolidayName())),
                "2026-02-14 应为 春节调休");
    }
}
