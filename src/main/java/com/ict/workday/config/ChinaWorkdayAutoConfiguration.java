package com.ict.workday.config;

import com.ict.workday.repository.*;
import com.ict.workday.service.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(HolidayRepository.class)
@EnableConfigurationProperties({AppProperties.class, CrawlProperties.class})
public class ChinaWorkdayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HolidayRepository.class)
    public HolidayRepository holidayRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createHolidayRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CrawlUrlRepository.class)
    @ConditionalOnCrawlEnabled
    public CrawlUrlRepository crawlUrlRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createCrawlUrlRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkdayJudgeService.class)
    public WorkdayJudgeService workdayJudgeService(HolidayRepository repository) {
        return new WorkdayJudgeService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(HolidayService.class)
    public HolidayService holidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        return new HolidayService(repository, workdayJudgeService);
    }

    @Bean
    @ConditionalOnMissingBean(JsonExportService.class)
    public JsonExportService jsonExportService(HolidayRepository repository) {
        return new JsonExportService(repository);
    }

    @Bean
    @ConditionalOnSchemaInit
    public DataSourceInitializer chinaWorkdaySchemaInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setEnabled(true);

        String script = resolveSchemaScript(dataSource);
        if (script == null) return initializer;

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
