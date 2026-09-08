package com.jankinwu.flynarwhal.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final FnAuthInterceptor fnAuthInterceptor;
    private final ClientVersionInterceptor clientVersionInterceptor;

    public WebMvcAuthConfig(FnAuthInterceptor fnAuthInterceptor, ClientVersionInterceptor clientVersionInterceptor) {
        this.fnAuthInterceptor = fnAuthInterceptor;
        this.clientVersionInterceptor = clientVersionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Version gate first: it is independent of the signature and rejects
        // outdated clients with a clear upgrade message before auth runs.
        // /api/config/** is exempt (handled inside the interceptor).
        registry.addInterceptor(clientVersionInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(fnAuthInterceptor)
                .addPathPatterns("/api/analysis/**")
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/danmu/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/download.html");
    }
}
