package com.jankinwu.flynarwhal.web.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ConfigService {

    String getDatabaseVersion();

    SseEmitter startUpdate(String downloadUrl, String hash, String proxyUrl);
}
