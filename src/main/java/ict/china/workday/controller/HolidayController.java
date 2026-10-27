package ict.china.workday.controller;

import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.service.HolidayService;
import ict.china.workday.service.WorkdayJudgeService;
import ict.china.workday.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/holiday")
public class HolidayController {

    private static final Logger log = LoggerFactory.getLogger(HolidayController.class);

    private final HolidayService holidayService;
    private final WorkdayJudgeService workdayJudgeService;

    public HolidayController(HolidayService holidayService, WorkdayJudgeService workdayJudgeService) {
        this.holidayService = holidayService;
        this.workdayJudgeService = workdayJudgeService;
    }

    @GetMapping("/is-workday")
    public ApiResponse<Map<String, Object>> isWorkday(@RequestParam("date") String date) {
        LocalDate d = parseDate(date);
        boolean isWorkday = workdayJudgeService.isWorkday(d);
        Optional<HolidayRecord> record = holidayService.findByDate(d);
        log.info("查询上班日 date={} result={}", date, isWorkday);

        Map<String, Object> data = new HashMap<>();
        data.put("date", d.toString());
        data.put("isWorkday", isWorkday);
        data.put("dayType", record.map(r -> r.getDayType().name()).orElse(isWorkday ? "WORKDAY" : "REST"));
        data.put("holidayName", record.map(HolidayRecord::getHolidayName).orElse(null));
        return ApiResponse.ok(data);
    }

    @GetMapping("/month")
    public ApiResponse<List<HolidayRecord>> month(@RequestParam("year") Integer year,
                                                  @RequestParam("month") Integer month) {
        validateYear(year);
        validateMonth(month);
        List<HolidayRecord> list = holidayService.findByMonth(year, month);
        log.info("查询月节假日 year={} month={} count={}", year, month, list.size());
        return ApiResponse.ok(list);
    }

    @GetMapping("/year")
    public ApiResponse<List<HolidayRecord>> year(@RequestParam("year") Integer year) {
        validateYear(year);
        List<HolidayRecord> list = holidayService.findByYear(year);
        log.info("查询年节假日 year={} count={}", year, list.size());
        return ApiResponse.ok(list);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 参数不能为空");
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    private void validateYear(Integer year) {
        if (year == null || year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year 必须为 4 位年份（1900-2100）");
        }
    }

    private void validateMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("month 必须在 1-12 之间");
        }
    }
}
