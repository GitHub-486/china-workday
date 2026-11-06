package com.ict.workday.controller;

import com.ict.workday.service.CrawlService;
import com.ict.workday.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final CrawlService crawlService;

    public AdminController(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @PostMapping("/api/admin/crawl")
    public ApiResponse<String> crawl() {
        String summary = crawlService.crawlOnce();
        log.info("手动触发抓取 result={}", summary);
        return ApiResponse.ok(summary);
    }
}
