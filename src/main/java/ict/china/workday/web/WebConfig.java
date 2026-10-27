package ict.china.workday.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将 token 拦截器应用到管理/写接口路径。
 * 读接口（/api/holiday/** 公开查询）不拦截；写/管理/导出路径拦截。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiTokenInterceptor interceptor;

    public WebConfig(ApiTokenInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/admin/**", "/api/holiday/export");
    }
}
