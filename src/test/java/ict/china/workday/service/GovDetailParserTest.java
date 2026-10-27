package ict.china.workday.service;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GovDetailParser 单元测试：设计文档 §13「解析器对样例节假日文本断言生成记录集合」。
 */
class GovDetailParserTest {

    private final GovDetailParser parser = new GovDetailParser();

    @Test
    void parse_rangeHolidayAndWorkday() {
        String html = "<html><body>"
                + "<h1>国务院办公厅关于2026年部分节假日安排的通知</h1>"
                + "<p>春节：2月15日至2月23日放假调休，共9天。2月14日（星期六）、2月28日（星期六）上班。</p>"
                + "</body></html>";
        List<HolidayRecord> records = parser.parse(html, 2026);

        // 放假区间 2/15 ~ 2/23 共 9 天 HOLIDAY
        assertTrue(records.stream().anyMatch(r ->
                r.getHolidayDate().equals(LocalDate.of(2026, 2, 15))
                        && r.getDayType() == DayType.HOLIDAY));
        assertTrue(records.stream().anyMatch(r ->
                r.getHolidayDate().equals(LocalDate.of(2026, 2, 23))
                        && r.getDayType() == DayType.HOLIDAY));

        // 调休上班 2/14、2/28 WORKDAY
        assertTrue(records.stream().anyMatch(r ->
                r.getHolidayDate().equals(LocalDate.of(2026, 2, 14))
                        && r.getDayType() == DayType.WORKDAY));
        assertTrue(records.stream().anyMatch(r ->
                r.getHolidayDate().equals(LocalDate.of(2026, 2, 28))
                        && r.getDayType() == DayType.WORKDAY));
    }

    @Test
    void parse_singleDayHoliday() {
        String html = "<html><body>"
                + "<p>元旦：1月1日放假，共1天。</p>"
                + "</body></html>";
        List<HolidayRecord> records = parser.parse(html, 2026);
        assertTrue(records.stream().anyMatch(r ->
                r.getHolidayDate().equals(LocalDate.of(2026, 1, 1))
                        && r.getDayType() == DayType.HOLIDAY));
    }

    @Test
    void parse_blankHtml_returnsEmpty() {
        assertTrue(parser.parse("", 2026).isEmpty());
        assertTrue(parser.parse(null, 2026).isEmpty());
    }

    @Test
    void parse_festivalNameDetected() {
        String html = "<html><body>"
                + "<p>国庆节、中秋节：10月1日至8日放假调休，共8天。</p>"
                + "</body></html>";
        List<HolidayRecord> records = parser.parse(html, 2026);
        assertEquals(DayType.HOLIDAY, records.get(0).getDayType());
        assertTrue(records.get(0).getHolidayName() != null);
    }

    @Test
    void parse_noDuplicateDates() {
        String html = "<html><body>"
                + "<p>五一劳动节：5月1日至5日放假调休，共5天。5月1日放假。</p>"
                + "</body></html>";
        List<HolidayRecord> records = parser.parse(html, 2026);
        long same = records.stream()
                .filter(r -> r.getHolidayDate().equals(LocalDate.of(2026, 5, 1)))
                .count();
        assertEquals(1L, same);
    }
}
