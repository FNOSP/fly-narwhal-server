package com.jankinwu.flynarwhal.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final FnAuthInterceptor fnAuthInterceptor;
    private final ClientVersionInterceptor clientVersionInterceptor;
    private final ClientCodeAuthInterceptor clientCodeAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcAuthConfig(FnAuthInterceptor fnAuthInterceptor,
                            ClientVersionInterceptor clientVersionInterceptor,
                            ClientCodeAuthInterceptor clientCodeAuthInterceptor,
                            AdminAuthInterceptor adminAuthInterceptor) {
        this.fnAuthInterceptor = fnAuthInterceptor;
        this.clientVersionInterceptor = clientVersionInterceptor;
        this.clientCodeAuthInterceptor = clientCodeAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Admin surface: gateway-guarded (filter) + fnOS admin check. No version
        // gate or client signature applies to the browser management page.
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");

        // Version gate: independent of the signature, rejects outdated clients
        // with a clear upgrade message before auth runs. /api/config/** and the
        // admin surface are exempt.
        registry.addInterceptor(clientVersionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**");

        // Per-client code auth first: it takes over only when Fnauth is present
        // and marks the request so the legacy interceptor defers.
        registry.addInterceptor(clientCodeAuthInterceptor)
                .addPathPatterns("/api/analysis/**")
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/danmu/**")
                .addPathPatterns("/api/client/**");
        registry.addInterceptor(fnAuthInterceptor)
                .addPathPatterns("/api/analysis/**")
                .addPathPatterns("/api/config/**")
                .addPathPatterns("/api/danmu/**")
                .addPathPatterns("/api/client/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // index.html is the Vue landing page (built from web/ into static/).
        registry.addRedirectViewController("/", "/index.html");
        // /download.html was the hand-written landing page the Vue app replaced. Forward
        // rather than redirect so links published in release notes and client configs keep
        // working with the address bar unchanged.
        registry.addViewController("/download.html").setViewName("forward:/index.html");
    }
}