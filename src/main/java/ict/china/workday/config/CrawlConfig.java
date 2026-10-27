package ict.china.workday.config;

import ict.china.workday.client.GovCrawlClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 抓取相关 bean 装配。
 */
@Configuration
public class CrawlConfig {

    @Bean
    public GovCrawlClient govCrawlClient(CrawlProperties crawlProperties) {
        return new GovCrawlClient(crawlProperties.getTimeoutMillis());
    }
}
