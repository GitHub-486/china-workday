package ict.china.workday.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级配置：api-token 凭据。
 * 配置文件前缀 china-workday.app。
 */
@ConfigurationProperties(prefix = "china-workday.app")
public class AppProperties {

    /**
     * 管理/写接口鉴权 token（L4 凭据，环境变量 CHINA_WORKDAY_API_TOKEN 注入）。
     * 缺失时写/管理/导出接口快速失败拒绝访问。
     */
    private String apiToken;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
