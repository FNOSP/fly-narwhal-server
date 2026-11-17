package com.jankinwu.flynarwhal.web.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.core.dto.request.ActivateClientRequest;
import com.jankinwu.flynarwhal.core.dto.response.ClientCodeInfoResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import com.jankinwu.flynarwhal.web.security.ClientCodeAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Per-client code endpoints. Both require a valid Fnauth signature (enforced by
 * the interceptor); the code plaintext never appears in any request or response.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/client")
public class ClientActivationController {

    private final ClientAuthCodeMapper codeMapper;

    @PostMapping("/activate")
    public Result<ClientCodeInfoResponse> activate(@RequestBody ActivateClientRequest request,
                                                   HttpServletRequest httpRequest) {
        ClientAuthCode code = (ClientAuthCode) httpRequest.getAttribute(ClientCodeAttributes.CLIENT_CODE);
        if (code == null) {
            return Result.error(com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_INVALID, "授权码无效");
        }
        // The interceptor already bound the device on first use; persist the label too.
        if (request.getDeviceLabel() != null && !request.getDeviceLabel().isBlank()
                && code.getBoundDeviceId() != null) {
            String label = request.getDeviceLabel().trim();
            if (label.length() > 256) {
                label = label.substring(0, 256);
            }
            codeMapper.update(null, new UpdateWrapper<ClientAuthCode>()
                    .eq("ID", code.getId())
                    .set("BOUND_DEVICE_LABEL", label)
                    .set("UPDATE_TIME", LocalDateTime.now()));
            code.setBoundDeviceLabel(label);
        }
        return Result.success(toInfo(code));
    }

    @GetMapping("/me")
    public Result<ClientCodeInfoResponse> me(HttpServletRequest httpRequest) {
        ClientAuthCode code = (ClientAuthCode) httpRequest.getAttribute(ClientCodeAttributes.CLIENT_CODE);
        if (code == null) {
            return Result.error(com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_INVALID, "授权码无效");
        }
        // Re-read so admin-side changes (disable/scope/unbind) surface immediately.
        ClientAuthCode fresh = codeMapper.selectById(code.getId());
        if (fresh == null) {
            return Result.error(com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_INVALID, "授权码无效");
        }
        return Result.success(toInfo(fresh));
    }

    private static ClientCodeInfoResponse toInfo(ClientAuthCode code) {
        Long expireMillis = code.getExpireTime() == null ? null
                : code.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return ClientCodeInfoResponse.builder()
                .name(code.getName())
                .expireTime(expireMillis)
                .permanent(code.getExpireTime() == null)
                .status(code.getStatus())
                .scope(code.getScope())
                .boundDeviceId(code.getBoundDeviceId())
                .boundDeviceLabel(code.getBoundDeviceLabel())
                .serverTime(System.currentTimeMillis())
                .build();
    }
}