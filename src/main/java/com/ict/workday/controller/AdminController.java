package com.ict.workday.controller;

import com.ict.workday.service.CrawlService;
import com.ict.workday.service.JsonExportService;
import com.ict.workday.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return jsonExportService.exportYear(year);
    }
}
