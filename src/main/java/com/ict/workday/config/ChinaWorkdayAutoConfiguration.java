package com.ict.workday.config;

import com.ict.workday.client.GovCrawlClient;
import com.ict.workday.repository.*;
import com.ict.workday.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@ConditionalOnClass(HolidayRepository.class)
@EnableConfigurationProperties({AppProperties.class, CrawlProperties.class})
public class ChinaWorkdayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChinaWorkdayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(HolidayRepository.class)
    public HolidayRepository holidayRepository(DataSource dataSource) {
        String dbType = JdbcUrlResolver.resolve(dataSource).name();
        log.info("china-workday 自动配置：HolidayRepository (数据库类型={})", dbType);
        return new HolidayRepositoryFactory(dataSource).createHolidayRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CrawlUrlRepository.class)
    @ConditionalOnCrawlEnabled
    public CrawlUrlRepository crawlUrlRepository(DataSource dataSource) {
        String dbType = JdbcUrlResolver.resolve(dataSource).name();
        log.info("china-workday 自动配置：CrawlUrlRepository (数据库类型={})", dbType);
        return new HolidayRepositoryFactory(dataSource).createCrawlUrlRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkdayJudgeService.class)
    public WorkdayJudgeService workdayJudgeService(HolidayRepository repository) {
        log.info("china-workday 自动配置：WorkdayJudgeService");
        return new WorkdayJudgeService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(HolidayService.class)
    public HolidayService holidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        log.info("china-workday 自动配置：HolidayService");
        return new HolidayService(repository, workdayJudgeService);
    }

    @Bean
    @ConditionalOnMissingBean(JsonExportService.class)
    public JsonExportService jsonExportService(HolidayRepository repository) {
        log.info("china-workday 自动配置：JsonExportService");
        return new JsonExportService(repository);
    }

    @Bean
    @ConditionalOnCrawlEnabled
    public GovCrawlClient govCrawlClient(CrawlProperties crawlProperties) {
        log.info("china-workday 自动配置：GovCrawlClient");
        return new GovCrawlClient(crawlProperties.getTimeoutMillis());
    }

    @Bean
    @ConditionalOnSchemaInit
    public DataSourceInitializer chinaWorkdaySchemaInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setEnabled(true);

        String script = resolveSchemaScript(dataSource);
        if (script == null) return initializer;

        String dbType = JdbcUrlResolver.resolve(dataSource).name();
        log.info("china-workday 初始化数据库表：{} ({})", script, dbType);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(script));
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    private String resolveSchemaScript(DataSource dataSource) {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL: return "sql/schema-postgres.sql";
            case H2:         return "sql/schema-h2.sql";
            case MYSQL:      return "sql/schema-mysql.sql";
            default:         return null;
        }
    }
}
