package ict.china.workday.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * gov.cn 抓取客户端：使用 JDK HttpClient 抓取列表页与详情页 HTML，jsoup 负责 HTML 解析。
 *
 * <p>安全防护（SEC-08 SSRF / SEC-12 重定向）：
 * <ul>
 *   <li>只允许 gov.cn 白名单域名（含子域），其它域名直接拒绝。</li>
 *   <li>禁止手动重定向，交由底层以受限策略处理；DNS 解析后校验 IP 为公网地址，
 *       阻断私有/环回/链路本地/组播（含云元数据 169.254.169.254）。</li>
 *   <li>统一超时，失败抛异常但不中断服务。</li>
 * </ul>
 */
public class GovCrawlClient {

    private static final Logger log = LoggerFactory.getLogger(GovCrawlClient.class);

    private static final Pattern GOV_DOMAIN = Pattern.compile(
            "(?i)^(?:[a-z0-9-]+\\.)*gov\\.cn$");

    private final int timeoutMillis;
    private final HttpClient httpClient;

    public GovCrawlClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 抓取页面 HTML（列表页或详情页），含 SSRF 校验。
     *
     * @param url 目标 URL
     * @return 页面 HTML 字符串；校验失败或不可达时抛 IllegalArgumentException
     */
    public String fetch(String url) {
        URI target = validateAndGet(url);
        int redirects = 0;
        while (true) {
            isValidTarget(target);
            HttpResponse<String> response = doGet(target);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                if (redirects >= 2 || !target.getScheme().equalsIgnoreCase("http")) {
                    throw new IllegalArgumentException("重定向次数超过上限或存在降级风险");
                }
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("重定向缺少 Location");
                }
                target = target.resolve(URI.create(location));
                redirects++;
                continue;
            }
            return response.body();
        }
    }

    /**
     * 抓取 JSON 数据（用于官方搜索接口），含 SSRF 校验。
     * 返回响应体字符串（JSON），由调用方解析。
     */
    public String fetchJson(String url) {
        URI target = validateAndGet(url);
        isValidTarget(target);
        HttpResponse<String> response = doGet(target);
        return response.body();
    }

    private HttpResponse<String> doGet(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ChinaWorkdayBot/1.0)")
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("抓取失败: {} - {}", uri, e.getMessage());
            throw new IllegalArgumentException("抓取失败: " + e.getMessage());
        }
    }

    /**
     * 校验并规范化 URL：必须是 http/https 且为 gov.cn 白名单域名。
     */
    private URI validateAndGet(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("仅允许 http/https 协议");
        }
        isValidTarget(uri);
        return uri;
    }

    /**
     * 核心校验：域名白名单 + DNS 解析后 IP 非私网（SEC-08）。
     */
    private void isValidTarget(URI uri) {
        String host = uri.getHost();
        if (host == null || !GOV_DOMAIN.matcher(host).matches()) {
            throw new IllegalArgumentException("目标不在 gov.cn 白名单内");
        }
        resolveAndCheckIp(host);
    }

    private void resolveAndCheckIp(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("目标解析为私有/保留地址，已阻断");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析目标域名");
        }
    }
}
