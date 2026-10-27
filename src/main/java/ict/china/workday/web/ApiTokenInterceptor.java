package ict.china.workday.web;

import ict.china.workday.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理/写接口 token 拦截器（SEC-01 认证 / SEC-05 快速失败）。
 * 凭据缺失或校验失败返回 401，不区分「未配置」与「不正确」（SEC-PRD-5 不泄露细节）。
 */
@Component
public class ApiTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Token";

    private final ApiTokenValidator validator;

    public ApiTokenInterceptor(ApiTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!validator.isConfigured()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权\"}");
            return false;
        }
        String token = request.getHeader(HEADER);
        if (!validator.validate(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权\"}");
            return false;
        }
        return true;
    }
}
