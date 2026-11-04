package com.ict.workday.service;

import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import com.ict.workday.domain.HolidayRecordDTO;
import com.ict.workday.domain.WorkdayResult;
import com.ict.workday.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    private final HolidayRepository repository;
    private final WorkdayJudgeService workdayJudgeService;

    public HolidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        this.repository = repository;
        this.workdayJudgeService = workdayJudgeService;
    }

    public WorkdayResult getWorkdayInfo(String dateStr) {
        LocalDate d = parseDate(dateStr);
        boolean isWorkday = workdayJudgeService.isWorkday(d);
        Optional<HolidayRecord> record = repository.findByDate(d);
        log.info("查询上班日 date={} result={}", dateStr, isWorkday);

        String typeName = record.map(r -> r.getDayType() == DayType.HOLIDAY ? "放假" : "调休上班")
                .orElse(isWorkday ? "工作日" : "休息日");
        return new WorkdayResult(
                d.toString(),
                isWorkday,
                record.map(r -> r.getDayType().name()).orElse(null),
                record.map(HolidayRecord::getHolidayName).orElse(null),
                typeName
        );
    }

    public List<HolidayRecordDTO> getMonthInfo(Integer year, Integer month) {
        validateYear(year);
        validateMonth(month);
        List<HolidayRecord> list = repository.findByMonth(year, month);
        log.info("查询月节假日 year={} month={} count={}", year, month, list.size());
        return list.stream().map(this::toMonthDTO).toList();
    }

    public List<HolidayRecordDTO> getYearInfo(Integer year) {
        validateYear(year);
        List<HolidayRecord> list = repository.findByYear(year);
        log.info("查询年节假日 year={} count={}", year, list.size());
        return list.stream().map(this::toFullDTO).toList();
    }

    public long countBySource(String source) {
        return repository.countBySource(source);
    }

    private HolidayRecordDTO toMonthDTO(HolidayRecord r) {
        HolidayRecordDTO dto = new HolidayRecordDTO();
        dto.setHolidayDate(r.getHolidayDate().toString());
        dto.setHolidayName(r.getHolidayName());
        dto.setDayType(r.getDayType().name());
        dto.setTypeName(r.getDayType() == DayType.HOLIDAY ? "放假" : "调休上班");
        dto.setYear(r.getYear());
        return dto;
    }

    private HolidayRecordDTO toFullDTO(HolidayRecord r) {
        HolidayRecordDTO dto = toMonthDTO(r);
        dto.setId(r.getId());
        dto.setSource(r.getSource());
        dto.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return dto;
    }

    public static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 参数不能为空");
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    public static void validateYear(Integer year) {
        if (year == null || year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year 必须为 4 位年份（1900-2100）");
        }
    }

    public static void validateMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("month 必须在 1-12 之间");
        }
    }
}
