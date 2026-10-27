package ict.china.workday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 抓取相关配置：抓取开关、列表页 URL、解析规则、定时 cron。
 * 配置文件前缀 china-workday.crawl。
 */
@ConfigurationProperties(prefix = "china-workday.crawl")
public class CrawlProperties {

    /** 是否启用抓取（默认开启）。 */
    private boolean enabled = true;

    /** 是否在应用启动时执行一次抓取。 */
    private boolean onStartup = true;

    /** 国务院政策文件库列表页 URL（列表页模式，部分环境被反爬）。 */
    private String listUrl = "https://www.gov.cn/zhengce/zhengceku/";

    /** 官方搜索数据接口（JSON），用于获取「部分节假日安排的通知」详情 URL。 */
    private String searchUrl = "https://sousuo.www.gov.cn/search-gov/data";

    /** 搜索关键字，用于在搜索接口检索节假日安排通知。 */
    private String searchKeyword = "部分节假日安排的通知";

    /** 搜索返回条数上限。 */
    private int searchPageSize = 20;

    /** 定时监控 cron（默认每日 08:00）。 */
    private String cron = "0 0 8 * * *";

    /** 请求超时（毫秒）。 */
    private int timeoutMillis = 10000;

    /** 列表页标题关键字，用于定位「部分节假日安排的通知」链接。 */
    private String listTitleKeyword = "部分节假日安排的通知";

    /** 抓取接口简单限频：同一时刻最大并发抓取数。 */
    private int maxConcurrent = 1;

    /** 保留年份向前偏移（含当前年）：即保留 [当前年-该值, 当前年+forwardOffset] 的数据。 */
    private int yearBackOffset = 1;

    /** 保留年份向后偏移：即保留 [当前年-backOffset, 当前年+该值] 的数据。 */
    private int yearForwardOffset = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOnStartup() {
        return onStartup;
    }

    public void setOnStartup(boolean onStartup) {
        this.onStartup = onStartup;
    }

    public String getListUrl() {
        return listUrl;
    }

    public void setListUrl(String listUrl) {
        this.listUrl = listUrl;
    }

    public String getSearchUrl() {
        return searchUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public int getSearchPageSize() {
        return searchPageSize;
    }

    public void setSearchPageSize(int searchPageSize) {
        this.searchPageSize = searchPageSize;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String getListTitleKeyword() {
        return listTitleKeyword;
    }

    public void setListTitleKeyword(String listTitleKeyword) {
        this.listTitleKeyword = listTitleKeyword;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getYearBackOffset() {
        return yearBackOffset;
    }

    public void setYearBackOffset(int yearBackOffset) {
        this.yearBackOffset = yearBackOffset;
    }

    public int getYearForwardOffset() {
        return yearForwardOffset;
    }

    public void setYearForwardOffset(int yearForwardOffset) {
        this.yearForwardOffset = yearForwardOffset;
    }
}
