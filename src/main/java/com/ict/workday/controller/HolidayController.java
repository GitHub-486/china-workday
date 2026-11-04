package com.ict.workday.controller;

import com.ict.workday.domain.HolidayRecordDTO;
import com.ict.workday.domain.WorkdayResult;
import com.ict.workday.service.HolidayService;
import com.ict.workday.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/holiday")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
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
}
