package com.ict.workday.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Cipher;

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

    // gov.cn 搜索页面 JS 内置的 RSA 公钥（用于加密 athenaAppKey）
    private static final String ATHENA_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCSMhMJQ+XLI7oW0k9Bwufur4Ag40tcsrzT7WZf6Ao0O/hyY1gZtCSYFxkxIZUXjW46j27XSW8IDX1rTJoHaMxHCWsOpTi2W5stybGYZytsY5on8gd8AIaS1d52h9eaS2TFydtJJtE50xHmT0WmoyoinWCuVCOkdCLhh9b9jSdeSQIDAQAB";
    // 明文 appKey（页面 JS 中固定不变）
    private static final String ATHENA_APP_KEY_PLAIN = "a46884b2013e4d189f2a8e2d49a23525";
    private static final String ATHENA_APP_NAME = "%E5%9B%BD%E7%BD%91%E6%90%9C%E7%B4%A2";

    // 缓存 RSA 加密后的 appKey（只在类加载时算一次，避免每次开销）
    private static final String ATHENA_RSA_APP_KEY;

    public static String getRsaAppKey() {
        return ATHENA_RSA_APP_KEY;
    }

    static {
        String encrypted = "";
        try {
            encrypted = rsaEncrypt(ATHENA_PUBLIC_KEY, ATHENA_APP_KEY_PLAIN);
        } catch (Exception e) {
            log.error("初始化 RSA appKey 失败", e);
        }
        ATHENA_RSA_APP_KEY = encrypted;
    }

    /**
     * RSA/ECB/PKCS1Padding 加密，返回 Base64 编码字符串。
     */
    private static String rsaEncrypt(String publicKeyPem, String data) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(spec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private final int timeoutMillis;
    private final HttpClient httpClient;

    public GovCrawlClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cm)
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

    /**
     * 以 GET + athenaHeaders 抓取 JSON（用于获取 athena codeCode）。
     */
    public String fetchAthenaGet(String url) {
        URI target = validateAndGet(url);
        isValidTarget(target);
        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ChinaWorkdayBot/1.0)")
                    .header("athenaAppKey", ATHENA_RSA_APP_KEY)
                    .header("athenaAppName", ATHENA_APP_NAME)
                    .header("Origin", "https://www.gov.cn")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("athena GET {} -> {}", url, response.statusCode());
            if (response.statusCode() != 200) {
                log.warn("athena GET 非 200: {} body={}", response.statusCode(), response.body());
                throw new IllegalArgumentException("athena GET 返回 " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            log.warn("athena GET 失败: {} - {}", url, e.getMessage());
            throw new IllegalArgumentException("athena GET 失败: " + e.getMessage());
        }
    }

    /**
     * 以 POST + JSON body + athena 请求头发送搜索请求。
     * 返回响应体字符串（JSON），由调用方解析。
     */
    public String fetchAthenaPost(String url, String jsonBody) {
        URI target = validateAndGet(url);
        isValidTarget(target);
        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ChinaWorkdayBot/1.0)")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("athenaAppKey", ATHENA_RSA_APP_KEY)
                    .header("athenaAppName", ATHENA_APP_NAME)
                    .header("Origin", "https://www.gov.cn")
                    .header("Referer", "https://www.gov.cn/zhengce/xxgk/index.htm")
                    .method("POST", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("athena POST {} -> {} ({}字节)", url, response.statusCode(), response.body().length());
            if (response.statusCode() != 200) {
                log.warn("athena POST 非 200: {} body={}", response.statusCode(), response.body());
                throw new IllegalArgumentException("athena POST 返回 " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            log.warn("athena POST 失败: {} - {}", url, e.getMessage());
            throw new IllegalArgumentException("athena POST 失败: " + e.getMessage());
        }
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
