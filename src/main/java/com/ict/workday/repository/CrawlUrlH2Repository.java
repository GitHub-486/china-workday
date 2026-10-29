package com.ict.workday.repository;

import org.springframework.jdbc.core.JdbcTemplate;

public class CrawlUrlH2Repository implements CrawlUrlRepository {

    private final JdbcTemplate jdbcTemplate;

    public CrawlUrlH2Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String url, int year) {
        try {
            jdbcTemplate.update("INSERT INTO crawl_url (url, year) VALUES (?, ?)", url, year);
        } catch (Exception e) {
        }
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
