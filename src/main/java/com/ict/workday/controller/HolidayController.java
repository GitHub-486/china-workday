package com.ict.workday.controller;

import com.ict.workday.domain.HolidayRecordDTO;
import com.ict.workday.domain.WorkdayResult;
import com.ict.workday.service.HolidayService;
import com.ict.workday.service.JsonExportService;
import com.ict.workday.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/holiday")
public class HolidayController {

    private final HolidayService holidayService;
    private final JsonExportService jsonExportService;

    public HolidayController(HolidayService holidayService, JsonExportService jsonExportService) {
        this.holidayService = holidayService;
        this.jsonExportService = jsonExportService;
    }

    @GetMapping("/is-workday")
    public ApiResponse<WorkdayResult> isWorkday(@RequestParam("date") String date) {
        return ApiResponse.ok(holidayService.getWorkdayInfo(date));
    }

    @GetMapping("/month")
    public ApiResponse<List<HolidayRecordDTO>> month(@RequestParam("year") Integer year,
                                                      @RequestParam("month") Integer month) {
        return ApiResponse.ok(holidayService.getMonthInfo(year, month));
    }

    @GetMapping("/year")
    public ApiResponse<List<HolidayRecordDTO>> year(@RequestParam("year") Integer year) {
        return ApiResponse.ok(holidayService.getYearInfo(year));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("year") Integer year) {
        return jsonExportService.exportYear(year);
    }
}
