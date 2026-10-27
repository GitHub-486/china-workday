package ict.china.workday.repository;

/**
 * 抓取 URL 去重访问接口。
 */
public interface CrawlUrlRepository {

    /**
     * 记录已抓取的 URL（幂等：忽略重复插入）。year 为信息列，不参与唯一约束。
     */
    void record(String url, int year);

    /**
     * 是否已抓取过该 URL。
     */
    boolean exists(String url);

    /**
     * 清空 crawl_url 表全部数据。
     */
    void truncate();
}
