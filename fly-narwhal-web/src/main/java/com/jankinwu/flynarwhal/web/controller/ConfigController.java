package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.config.BuildVersionConfiguration;
import com.jankinwu.flynarwhal.web.security.FnAuthService;
import com.jankinwu.flynarwhal.web.service.ConfigService;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jankinwu.flynarwhal.web.dto.request.ServerUpdateRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    @PostMapping("/update/start")
    public SseEmitter startUpdate(@RequestBody ServerUpdateRequest request) {
        return configService.startUpdate(request.getDownloadUrl(), request.getHash(), request.getProxyUrl());
    }

    private final FnAuthService fnAuthService;
//    private final FnAuthConfigService fnAuthConfigService;

    @PostMapping("/auth-code")
    public Result<String> getAuthCode() {
        return Result.success(fnAuthService.getOrGenerateAuthCode());
    }

//    @PostMapping("/fn-base-url")
//    public Result<Void> setFnBaseUrl(@RequestBody SetFnBaseUrlRequest request, HttpServletRequest httpRequest) {
//        try {
//            String baseUrl = request.getBaseUrl();
//            String authorization = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
//            String cookie = httpRequest.getHeader(HttpHeaders.COOKIE);
//            boolean ok = fnAuthService.validateOnceAgainstBaseUrl(baseUrl, authorization, cookie);
//            if (!ok) {
//                return Result.error(401, "Unauthorized");
//            }
//            fnAuthConfigService.setFnBaseUrl(baseUrl);
//            return Result.success();
//        } catch (IllegalArgumentException e) {
//            return Result.error(400, e.getMessage());
//        } catch (Exception e) {
//            log.error("Error setting fn base url", e);
//            return Result.error("Error: " + e.getMessage());
//        }
//    }

    @GetMapping("/version")
    public Result<String> getDbVersion() {
        try {
            String version = configService.getDatabaseVersion();
            if (BuildVersionConfiguration.IS_FN_APP) {
                version = version + "-fnapp";
            }
            return Result.success(version);
        } catch (Exception e) {
            log.error("Error getting database version", e);
            return Result.error("Error: " + e.getMessage());
        }
    }
}
