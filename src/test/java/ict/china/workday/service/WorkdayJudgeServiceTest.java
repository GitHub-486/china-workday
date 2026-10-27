package ict.china.workday.service;

import ict.china.workday.domain.DayType;
import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkdayJudgeService 单元测试：覆盖 HOLIDAY/WORKDAY/普通周末/普通工作日 组合（设计文档 §13）。
 */
class WorkdayJudgeServiceTest {

    private HolidayRepository repository;
    private WorkdayJudgeService service;

    @BeforeEach
    void setUp() {
        repository = mock(HolidayRepository.class);
        service = new WorkdayJudgeService(repository);
    }

    @Test
    void normalWeekday_shouldBeWorkday() {
        LocalDate date = LocalDate.of(2026, 2, 16); // 周一
        when(repository.findByDate(any())).thenReturn(Optional.empty());
        assertTrue(service.isWorkday(date));
    }

    @Test
    void normalWeekend_shouldNotBeWorkday() {
        LocalDate date = LocalDate.of(2026, 2, 21); // 周六
        when(repository.findByDate(any())).thenReturn(Optional.empty());
        assertFalse(service.isWorkday(date));
    }

    @Test
    void holidayOnWeekday_shouldNotBeWorkday() {
        LocalDate date = LocalDate.of(2026, 2, 17); // 春节放假
        HolidayRecord rec = new HolidayRecord(date, "春节", DayType.HOLIDAY, 2026, "gov.cn");
        when(repository.findByDate(any())).thenReturn(Optional.of(rec));
        assertFalse(service.isWorkday(date));
    }

    @Test
    void workdayOnWeekend_shouldBeWorkday() {
        LocalDate date = LocalDate.of(2026, 2, 14); // 周六调休上班
        HolidayRecord rec = new HolidayRecord(date, "春节调休", DayType.WORKDAY, 2026, "gov.cn");
        when(repository.findByDate(any())).thenReturn(Optional.of(rec));
        assertTrue(service.isWorkday(date));
    }

    @Test
    void holidayOnWeekend_shouldNotBeWorkday() {
        LocalDate date = LocalDate.of(2026, 2, 15); // 周日放假日
        HolidayRecord rec = new HolidayRecord(date, "春节", DayType.HOLIDAY, 2026, "gov.cn");
        when(repository.findByDate(any())).thenReturn(Optional.of(rec));
        assertFalse(service.isWorkday(date));
    }
}
