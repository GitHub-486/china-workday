package ict.china.workday.controller;

import ict.china.workday.domain.DayType;
import ict.china.workday.repository.HolidayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试（H2）：is-workday 接口正常与边界用例（设计文档 §13 验收用例）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HolidayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HolidayRepository repository;

    private void seed(LocalDate date, String name, DayType type) {
        int year = date.getYear();
        repository.upsert(seedRecord(date, name, type, year));
    }

    private ict.china.workday.domain.HolidayRecord seedRecord(LocalDate date, String name, DayType type, int year) {
        ict.china.workday.domain.HolidayRecord r =
                new ict.china.workday.domain.HolidayRecord(date, name, type, year, "gov.cn");
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    void isWorkday_holiday_shouldReturnFalse() throws Exception {
        seed(LocalDate.of(2026, 2, 15), "春节", DayType.HOLIDAY);
        mockMvc.perform(get("/workday/api/holiday/is-workday").param("date", "2026-02-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isWorkday").value(false))
                .andExpect(jsonPath("$.data.dayType").value("HOLIDAY"))
                .andExpect(jsonPath("$.data.holidayName").value("春节"));
    }

    @Test
    void isWorkday_workdayOnWeekend_shouldReturnTrue() throws Exception {
        seed(LocalDate.of(2026, 2, 14), "春节调休", DayType.WORKDAY);
        mockMvc.perform(get("/workday/api/holiday/is-workday").param("date", "2026-02-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isWorkday").value(true))
                .andExpect(jsonPath("$.data.dayType").value("WORKDAY"));
    }

    @Test
    void isWorkday_plainSunday_shouldReturnFalse() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/is-workday").param("date", "2026-02-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isWorkday").value(false));
    }

    @Test
    void isWorkday_invalidDateFormat_shouldReturn400() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/is-workday").param("date", "2026/02/14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void isWorkday_missingDate_shouldReturn400() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/is-workday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void month_invalidMonth13_shouldReturn400() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/month").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void month_emptyParams_shouldReturn400() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void export_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/export").param("year", "2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void export_wrongToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/workday/api/holiday/export").param("year", "2026")
                        .header("X-API-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void favicon_shouldReturn404_not500() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
    }
}
