package com.jankinwu.flynarwhal.web.config;

import com.jankinwu.flynarwhal.web.gateway.GatewayToken;
import com.jankinwu.flynarwhal.web.security.AdminGatewayGuardFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the admin gateway guard ahead of every other filter (including
 * RequestCachingFilter) so unauthorized /api/admin/** requests are rejected
 * before any body is read.
 */
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<AdminGatewayGuardFilter> adminGatewayGuardFilter(GatewayToken token) {
        FilterRegistrationBean<AdminGatewayGuardFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminGatewayGuardFilter(token));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("adminGatewayGuardFilter");
        return registration;
    }
}