package ict.china.workday.controller;

import ict.china.workday.service.CrawlService;
import ict.china.workday.service.JsonExportService;
import ict.china.workday.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final CrawlService crawlService;
    private final JsonExportService jsonExportService;

    public AdminController(CrawlService crawlService, JsonExportService jsonExportService) {
        this.crawlService = crawlService;
        this.jsonExportService = jsonExportService;
    }

    @PostMapping("/api/admin/crawl")
    public ApiResponse<String> crawl() {
        String summary = crawlService.crawlOnce();
        log.info("手动触发抓取 result={}", summary);
        return ApiResponse.ok(summary);
    }

    @GetMapping("/api/holiday/export")
    public ResponseEntity<byte[]> export(@RequestParam("year") Integer year) {
        if (year == null || year < 1900 || year > 2100) {
            throw new IllegalArgumentException("year 必须为 4 位年份（1900-2100）");
        }
        Map<String, Object> data = jsonExportService.export(year);
        log.info("导出年度JSON year={} count={}", year, data.get("days") == null ? 0
                : ((java.util.List<?>) data.get("days")).size());
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + year + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
