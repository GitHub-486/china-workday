package com.ict.workday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ict.workday.domain.HolidayRecord;
import com.ict.workday.repository.HolidayRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JsonExportService {

    private final HolidayRepository repository;
    private final ObjectMapper objectMapper;

    public JsonExportService(HolidayRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ResponseEntity<byte[]> exportYear(int year) {
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year 必须为 4 位年份（1900-2100）");
        }
        List<HolidayRecord> records = repository.findByYear(year);
        Map<String, Object> data = buildResult(year, records);
        try {
            byte[] bytes = objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + year + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> exportMonth(int year, int month) {
        List<HolidayRecord> records = repository.findByMonth(year, month);
        return buildResult(year, records);
    }

    private Map<String, Object> buildResult(int year, List<HolidayRecord> records) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (HolidayRecord r : records) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("holidayDate", r.getHolidayDate().toString());
            day.put("holidayName", r.getHolidayName());
            day.put("dayType", r.getDayType().name());
            day.put("typeName", r.getDayType().getDisplayName());
            day.put("year", r.getYear());
            days.add(day);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("year", year);
        root.put("generatedAt", LocalDate.now().toString());
        root.put("source", "gov.cn");
        root.put("days", days);
        return root;
    }
}
