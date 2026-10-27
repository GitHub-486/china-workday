package ict.china.workday.scheduler;

import ict.china.workday.config.CrawlProperties;
import ict.china.workday.service.CrawlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 抓取调度：应用启动时（可配）+ 定时监控（默认每日 08:00）。
 */
@Component
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final CrawlProperties crawlProperties;
    private final CrawlService crawlService;

    public CrawlScheduler(CrawlProperties crawlProperties, CrawlService crawlService) {
        this.crawlProperties = crawlProperties;
        this.crawlService = crawlService;
    }

    /**
     * 应用启动后主动抓取一次（可配 china-workday.crawl.on-startup）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCrawl() {
        if (!crawlProperties.isEnabled() || !crawlProperties.isOnStartup()) {
            log.info("跳过启动抓取（enabled={}, onStartup={}）",
                    crawlProperties.isEnabled(), crawlProperties.isOnStartup());
            return;
        }
        log.info("应用启动，开始首次抓取...");
        try {
            log.info(crawlService.crawlOnce(false));
        } catch (Exception e) {
            log.warn("启动抓取异常（不影响服务）: {}", e.getMessage());
        }
    }

    /**
     * 定时监控抓取。
     */
    @Scheduled(cron = "${china-workday.crawl.cron:0 0 8 * * *}")
    public void scheduledCrawl() {
        if (!crawlProperties.isEnabled()) {
            return;
        }
        log.info("定时抓取开始...");
        try {
            log.info(crawlService.crawlOnce(false));
        } catch (Exception e) {
            log.warn("定时抓取异常（不影响服务）: {}", e.getMessage());
        }
    }
}
