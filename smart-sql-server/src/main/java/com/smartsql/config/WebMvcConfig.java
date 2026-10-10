package com.smartsql.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 允许的前端来源（逗号分隔）。
     *
     * ⚠️ 原来是 allowedOriginPatterns("*") 叠加 allowCredentials(true)：
     * 等于对所有来源开放且允许携带凭据，任意站点都能直读会话与知识库数据。
     * 收窄为显式白名单后，浏览器会拒绝白名单外的跨站请求。
     */
    @Value("${smart-sql.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 注意：方法列表保持原样（前端确实在用 PUT/DELETE），本次只收紧来源
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
