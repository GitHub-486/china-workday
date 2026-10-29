package com.ict.workday.config;

import com.ict.workday.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class HolidayRepositoryFactory {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public HolidayRepositoryFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public HolidayRepository createHolidayRepository() {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL:
                return new HolidayPostgresRepository(jdbcTemplate);
            case H2:
                return new HolidayH2Repository(jdbcTemplate);
            case MYSQL:
                return new HolidayMysqlRepository(jdbcTemplate);
            default:
                throw new IllegalStateException(
                        "不支持的数据库类型。当前仅支持 PostgreSQL / H2 / MySQL。");
        }
    }

    public CrawlUrlRepository createCrawlUrlRepository() {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL:
                return new CrawlUrlPostgresRepository(jdbcTemplate);
            case H2:
                return new CrawlUrlH2Repository(jdbcTemplate);
            case MYSQL:
                return new CrawlUrlMysqlRepository(jdbcTemplate);
            default:
                throw new IllegalStateException(
                        "不支持的数据库类型。当前仅支持 PostgreSQL / H2 / MySQL。");
        }
    }
}
