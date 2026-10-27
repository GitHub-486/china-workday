package ict.china.workday.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * crawl_url 表的 JdbcTemplate 实现（SEC-06 参数化）。
 */
@Repository
public class CrawlUrlJdbcRepository implements CrawlUrlRepository {

    private final JdbcTemplate jdbcTemplate;

    public CrawlUrlJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String url, int year) {
        try {
            jdbcTemplate.update("INSERT INTO crawl_url (url, year) VALUES (?, ?)", url, year);
        } catch (Exception e) {
            // 唯一约束冲突：已记录过，幂等忽略
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
