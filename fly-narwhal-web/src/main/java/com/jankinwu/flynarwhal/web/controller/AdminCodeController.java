package com.jankinwu.flynarwhal.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.dto.request.CodeScopeRequest;
import com.jankinwu.flynarwhal.web.dto.request.CodeStatusRequest;
import com.jankinwu.flynarwhal.web.dto.request.CreateCodeRequest;
import com.jankinwu.flynarwhal.web.dto.response.AccessLogItem;
import com.jankinwu.flynarwhal.web.dto.response.AdminCodeItem;
import com.jankinwu.flynarwhal.web.dto.response.AdminCreateCodeResponse;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAccessLogMapper;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import com.jankinwu.flynarwhal.web.security.ClientCodeAuthService;
import com.jankinwu.flynarwhal.web.service.LegacyAuthSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin management of per-client codes and access visibility. Reachable only
 * through the fnOS gateway socket (AdminGatewayGuardFilter) and only for
 * administrators (AdminAuthInterceptor); direct TCP access gets 404.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminCodeController {

    private final ClientAuthCodeMapper codeMapper;
    private final ClientAccessLogMapper accessLogMapper;
    private final ClientCodeAuthService authService;
    private final LegacyAuthSettingsService legacySettings;

    @GetMapping("/session")
    public Result<Map<String, Object>> session(jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", request.getHeader("X-Trim-Userid"));
        info.put("username", request.getHeader("X-Trim-Username"));
        info.put("isAdmin", "true".equalsIgnoreCase(request.getHeader("X-Trim-Isadmin")));
        return Result.success(info);
    }

    @GetMapping("/codes")
    public Result<List<AdminCodeItem>> listCodes() {
        List<ClientAuthCode> rows = codeMapper.selectList(
                new QueryWrapper<ClientAuthCode>().orderByDesc("ID"));
        return Result.success(rows.stream().map(AdminCodeController::toItem).toList());
    }

    @PostMapping("/codes")
    public Result<AdminCreateCodeResponse> createCode(@RequestBody CreateCodeRequest request) {
        try {
            String name = request.getName() == null ? "" : request.getName().trim();
            if (name.isEmpty() || name.length() > 128) {
                return Result.error("名称不能为空且不超过 128 字符");
            }
            Integer days = request.getExpireDays();
            if (days != null && (days < 1 || days > 36500)) {
                return Result.error("有效期天数须在 1–36500 之间");
            }
            ClientCodeAuthService.CreatedCode created = authService.createCode(name, days, request.getScope());
            return Result.success(new AdminCreateCodeResponse(created.id(), created.code(), created.expireTime()));
        } catch (Exception e) {
            log.error("Error creating client code", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/codes/{id}/status")
    public Result<Void> setStatus(@PathVariable("id") Long id, @RequestBody CodeStatusRequest request) {
        if (request.getEnabled() == null) {
            return Result.error("enabled 不能为空");
        }
        int updated = codeMapper.update(null, new UpdateWrapper<ClientAuthCode>()
                .eq("ID", id)
                .set("STATUS", request.getEnabled() ? ClientAuthCode.STATUS_ACTIVE : ClientAuthCode.STATUS_DISABLED)
                .set("UPDATE_TIME", LocalDateTime.now()));
        return updated > 0 ? Result.success() : Result.error("授权码不存在");
    }

    @PostMapping("/codes/{id}/scope")
    public Result<Void> setScope(@PathVariable("id") Long id, @RequestBody CodeScopeRequest request) {
        String scope = request.getScope();
        if (!ClientAuthCode.SCOPE_READ_WRITE.equals(scope) && !ClientAuthCode.SCOPE_READ_ONLY.equals(scope)) {
            return Result.error("scope 须为 READ_WRITE 或 READ_ONLY");
        }
        int updated = codeMapper.update(null, new UpdateWrapper<ClientAuthCode>()
                .eq("ID", id)
                .set("SCOPE", scope)
                .set("UPDATE_TIME", LocalDateTime.now()));
        return updated > 0 ? Result.success() : Result.error("授权码不存在");
    }

    @DeleteMapping("/codes/{id}")
    public Result<Void> deleteCode(@PathVariable("id") Long id) {
        int removed = codeMapper.deleteById(id);
        return removed > 0 ? Result.success() : Result.error("授权码不存在");
    }

    @GetMapping("/access/logs")
    public Result<List<AccessLogItem>> accessLogs(@RequestParam(value = "codeId", required = false) Long codeId,
                                                  @RequestParam(value = "event", required = false) String event,
                                                  @RequestParam(value = "limit", defaultValue = "200") int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        QueryWrapper<ClientAccessLog> query = new QueryWrapper<ClientAccessLog>()
                .orderByDesc("LAST_TIME")
                .last("LIMIT " + capped);
        if (codeId != null) {
            query.eq("CODE_ID", codeId);
        }
        if (event != null && !event.isBlank()) {
            query.eq("EVENT", event);
        }
        List<ClientAccessLog> rows = accessLogMapper.selectList(query);
        return Result.success(rows.stream().map(AdminCodeController::toLogItem).toList());
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        LegacyAuthSettingsService.LegacyStats stats = legacySettings.recentLegacyStats();
        Long totalCodes = codeMapper.selectCount(new QueryWrapper<>());
        Long activeCodes = codeMapper.selectCount(new QueryWrapper<ClientAuthCode>()
                .eq("STATUS", ClientAuthCode.STATUS_ACTIVE));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("legacyAuthEnabled", legacySettings.isEnabled());
        info.put("legacyRequests24h", stats.requests24h());
        info.put("legacyDistinctIps24h", stats.distinctIps24h());
        info.put("totalCodes", totalCodes == null ? 0 : totalCodes);
        info.put("activeCodes", activeCodes == null ? 0 : activeCodes);
        return Result.success(info);
    }

    @PostMapping("/legacy-auth")
    public Result<Void> setLegacyAuth(@RequestBody CodeScopeRequest request) {
        if (request.getEnabled() == null) {
            return Result.error("enabled 不能为空");
        }
        legacySettings.setEnabled(request.getEnabled());
        return Result.success();
    }

    private static AdminCodeItem toItem(ClientAuthCode code) {
        return AdminCodeItem.builder()
                .id(code.getId())
                .name(code.getName())
                .status(code.getStatus())
                .scope(code.getScope())
                .expireTime(code.getExpireTime())
                .permanent(code.getExpireTime() == null)
                .createTime(code.getCreateTime())
                .boundInstanceId(code.getBoundInstanceId())
                .boundInstanceLabel(code.getBoundInstanceLabel())
                .boundTime(code.getBoundTime())
                .lastUsedTime(code.getLastUsedTime())
                .lastUsedIp(code.getLastUsedIp())
                .lastUsedVersion(code.getLastUsedVersion())
                .build();
    }

    private static AccessLogItem toLogItem(ClientAccessLog row) {
        return AccessLogItem.builder()
                .id(row.getId())
                .codeId(row.getCodeId())
                .codeName(row.getCodeName())
                .authMode(row.getAuthMode())
                .clientIp(row.getClientIp())
                .clientVersion(row.getClientVersion())
                .instanceId(row.getInstanceId())
                .event(row.getEvent())
                .hitCount(row.getHitCount())
                .firstTime(row.getFirstTime())
                .lastTime(row.getLastTime())
                .build();
    }
}