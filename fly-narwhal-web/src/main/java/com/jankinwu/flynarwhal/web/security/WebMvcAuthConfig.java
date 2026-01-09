package com.jankinwu.flynarwhal.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final FnAuthInterceptor fnAuthInterceptor;

    public WebMvcAuthConfig(FnAuthInterceptor fnAuthInterceptor) {
        this.fnAuthInterceptor = fnAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(fnAuthInterceptor)
                .addPathPatterns("/api/analysis/**")
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/danmu/**");
    }
}

