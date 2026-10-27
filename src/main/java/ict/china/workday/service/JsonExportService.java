package ict.china.workday.service;

import ict.china.workday.domain.HolidayRecord;
import ict.china.workday.repository.HolidayRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按年份导出 JSON（供引用方放入自身 resources 离线使用）。
 * 生成 {year}.json 数据，days 中记录字段与查询接口完全一致。
 */
@Service
public class JsonExportService {

    private final HolidayRepository repository;

    public JsonExportService(HolidayRepository repository) {
        this.repository = repository;
    }

    public java.util.Map<String, Object> export(int year) {
        List<HolidayRecord> records = repository.findByYear(year);
        return buildResult(year, records);
    }

    public java.util.Map<String, Object> exportMonth(int year, int month) {
        List<HolidayRecord> records = repository.findByMonth(year, month);
        return buildResult(year, records);
    }

    private java.util.Map<String, Object> buildResult(int year, List<HolidayRecord> records) {
        java.util.List<java.util.Map<String, Object>> days = new java.util.ArrayList<>();
        for (HolidayRecord r : records) {
            java.util.Map<String, Object> day = new java.util.LinkedHashMap<>();
            day.put("holidayDate", r.getHolidayDate().toString());
            day.put("holidayName", r.getHolidayName());
            day.put("dayType", r.getDayType().name());
            day.put("year", r.getYear());
            days.add(day);
        }
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("year", year);
        root.put("generatedAt", java.time.LocalDate.now().toString());
        root.put("source", "gov.cn");
        root.put("days", days);
        return root;
    }
}
