package com.faust.easychat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.faust.easychat.filter.JwtAuthFilter;
import com.faust.easychat.filter.TraceIdFilter;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceIdFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(0);
        return bean;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService,
            @Value("${jwt.public-paths:/swagger-ui,/v3/api-docs}") String publicPaths) {
        return new JwtAuthFilter(jwtUtil, tokenBlacklistService, publicPaths);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
