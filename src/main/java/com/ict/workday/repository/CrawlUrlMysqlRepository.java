package com.ict.workday.repository;

import org.springframework.jdbc.core.JdbcTemplate;

public class CrawlUrlMysqlRepository implements CrawlUrlRepository {

    private final JdbcTemplate jdbcTemplate;

    public CrawlUrlMysqlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String url, int year) {
        jdbcTemplate.update("INSERT IGNORE INTO crawl_url (url, year) VALUES (?, ?)", url, year);
    }

    @Override
    public boolean exists(String url) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crawl_url WHERE url = ?", Integer.class, url);
        return c != null && c > 0;
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM crawl_url");
    }
}
