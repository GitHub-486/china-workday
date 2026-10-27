package ict.china.workday.web;

import ict.china.workday.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API Token 门禁（SEC-01 认证 / SEC-05 凭据 / SEC-11 token 安全）。
 * 比较使用常量时间比较，避免时序侧信道。
 */
@Component
public class ApiTokenValidator {

    private final AtomicReference<String> expectedToken = new AtomicReference<>();

    public ApiTokenValidator(AppProperties appProperties) {
        this.expectedToken.set(appProperties.getApiToken());
    }

    /**
     * 凭据是否已配置。缺失时写/管理接口应快速失败（SEC-05 不静默放行）。
     */
    public boolean isConfigured() {
        return expectedToken.get() != null && !expectedToken.get().isBlank();
    }

    /**
     * 校验请求 token 与配置 token 是否一致（常量时间比较）。
     */
    public boolean validate(String requestToken) {
        String expected = expectedToken.get();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        if (requestToken == null || requestToken.isBlank()) {
            return false;
        }
        return constantTimeEquals(expected, requestToken);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
