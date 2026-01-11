package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.web.security.FnAuthService;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config")
public class ConfigController {

//    private final FnAuthService fnAuthService;
//
//    private final FnAuthConfigService fnAuthConfigService;

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
}
