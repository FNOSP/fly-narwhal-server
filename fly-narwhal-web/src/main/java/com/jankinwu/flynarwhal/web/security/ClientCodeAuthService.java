package com.jankinwu.flynarwhal.web.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.core.dto.response.ResultCodes;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import com.jankinwu.flynarwhal.web.service.ClientAccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and verifies per-client authorization codes (FNK2).
 *
 * <p>Verification order: timestamp window → keyid lookup → status/expiry →
 * HMAC (constant-time) → nonce replay → first-use device binding. Failures are
 * values ({@link Outcome}), never exceptions, and every outcome feeds the
 * access log. The plaintext code exists only inside {@link #createCode}.
 */
@Slf4j
@Service
public class ClientCodeAuthService {

    private static final long TS_WINDOW_MS = 5 * 60_000L;
    private static final int NONCE_MAX_PER_KEY = 8192;

    private final ClientAuthCodeMapper codeMapper;
    private final ClientAccessLogService accessLog;
    private final AuthRateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    /** keyid -> (nonce -> first-seen ms); trimmed inline, no timer. */
    private final Map<String, Map<String, Long>> nonces = new ConcurrentHashMap<>();

    public ClientCodeAuthService(ClientAuthCodeMapper codeMapper,
                                 ClientAccessLogService accessLog,
                                 AuthRateLimiter rateLimiter) {
        this.codeMapper = codeMapper;
        this.accessLog = accessLog;
        this.rateLimiter = rateLimiter;
    }

    /** Result of one verification: either the authenticated code or a business error. */
    public record Outcome(ClientAuthCode code, int errorCode, String event) {
        public boolean ok() {
            return code != null;
        }

        static Outcome ok(ClientAuthCode code) {
            return new Outcome(code, 0, ClientAccessLog.EVENT_OK);
        }

        static Outcome fail(int errorCode, String event) {
            return new Outcome(null, errorCode, event);
        }
    }

    /** Creates a code and returns the plaintext exactly once. */
    public CreatedCode createCode(String name, Integer expireDays, String scope) {
        byte[] secret = new byte[ClientCodeCrypto.SECRET_LEN];
        random.nextBytes(secret);
        String fullCode = ClientCodeCrypto.buildCode(secret);
        String keyId = ClientCodeCrypto.keyIdHex(fullCode);
        byte[] signKey = ClientCodeCrypto.deriveSignKey(secret);

        ClientAuthCode row = new ClientAuthCode();
        row.setName(name);
        row.setCodeHash(keyId);
        row.setSignKey(Base64.getEncoder().encodeToString(signKey));
        row.setStatus(ClientAuthCode.STATUS_ACTIVE);
        row.setScope(ClientAuthCode.SCOPE_READ_ONLY.equals(scope)
                ? ClientAuthCode.SCOPE_READ_ONLY : ClientAuthCode.SCOPE_READ_WRITE);
        row.setExpireTime(expireDays == null ? null : LocalDateTime.now().plusDays(expireDays));
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        codeMapper.insert(row);
        return new CreatedCode(row.getId(), fullCode, row.getExpireTime());
    }

    public record CreatedCode(Long id, String code, LocalDateTime expireTime) {
    }

    /**
     * Verifies one Fnauth-signed request. {@code header} is the raw Fnauth value
     * {@code keyid=..&ts=..&nonce=..&sig=..}.
     */
    public Outcome verify(String header, String method, String url,
                          Map<String, String[]> parameters, byte[] body,
                          String instanceId, String ip, String clientVersion) {
        if (rateLimiter.isBlocked(ip)) {
            accessLog.record(null, null, ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_RATE_LIMITED);
            return Outcome.fail(ResultCodes.CODE_RATE_LIMITED, ClientAccessLog.EVENT_RATE_LIMITED);
        }

        Map<String, String> parts = parseHeader(header);
        String keyId = parts.get("keyid");
        String ts = parts.get("ts");
        String nonce = parts.get("nonce");
        String sig = parts.get("sig");
        if (keyId == null || ts == null || nonce == null || sig == null
                || keyId.length() != 64 || nonce.length() != 32) {
            return failInvalid(ip, clientVersion, instanceId, null, null);
        }

        long tsMs;
        try {
            tsMs = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return failInvalid(ip, clientVersion, instanceId, null, null);
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - tsMs) > TS_WINDOW_MS) {
            accessLog.record(null, null, ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_REPLAYED);
            rateLimiter.recordFailure(ip);
            return Outcome.fail(ResultCodes.CODE_REPLAYED, ClientAccessLog.EVENT_REPLAYED);
        }

        ClientAuthCode code = codeMapper.selectOne(
                new QueryWrapper<ClientAuthCode>().eq("CODE_HASH", keyId));
        if (code == null) {
            return failInvalid(ip, clientVersion, instanceId, null, null);
        }

        if (ClientAuthCode.STATUS_DISABLED.equals(code.getStatus())) {
            accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_DISABLED);
            rateLimiter.recordFailure(ip);
            return Outcome.fail(ResultCodes.CODE_DISABLED, ClientAccessLog.EVENT_DISABLED);
        }
        if (code.getExpireTime() != null && code.getExpireTime().isBefore(LocalDateTime.now())) {
            accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_EXPIRED);
            rateLimiter.recordFailure(ip);
            return Outcome.fail(ResultCodes.CODE_EXPIRED, ClientAccessLog.EVENT_EXPIRED);
        }

        byte[] signKey;
        try {
            signKey = Base64.getDecoder().decode(code.getSignKey());
        } catch (IllegalArgumentException e) {
            log.error("Stored sign key for code {} is corrupt", code.getId());
            return failInvalid(ip, clientVersion, instanceId, code.getId(), code.getName());
        }
        String dataJsonMd5 = AuthCanonicalization.dataJsonMd5(parameters, body);
        String expected = ClientCodeCrypto.requestSignature(signKey, method, url, ts, nonce, dataJsonMd5, instanceId);
        if (!ClientCodeCrypto.constantTimeEquals(expected, sig)) {
            return failInvalid(ip, clientVersion, instanceId, code.getId(), code.getName());
        }

        if (!registerNonce(keyId, nonce, now)) {
            accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_REPLAYED);
            return Outcome.fail(ResultCodes.CODE_REPLAYED, ClientAccessLog.EVENT_REPLAYED);
        }

        // First-use instance binding: an unbound code adopts this instance atomically.
        if (code.getBoundInstanceId() == null || code.getBoundInstanceId().isBlank()) {
            if (instanceId != null && !instanceId.isBlank()) {
                int updated = codeMapper.update(null, new UpdateWrapper<ClientAuthCode>()
                        .eq("ID", code.getId())
                        .isNull("BOUND_INSTANCE_ID")
                        .set("BOUND_INSTANCE_ID", instanceId)
                        .set("BOUND_TIME", LocalDateTime.now())
                        .set("UPDATE_TIME", LocalDateTime.now()));
                if (updated > 0) {
                    code.setBoundInstanceId(instanceId);
                } else {
                    // Another request bound it first: re-read and fall through to the check.
                    ClientAuthCode fresh = codeMapper.selectById(code.getId());
                    if (fresh != null) {
                        code.setBoundInstanceId(fresh.getBoundInstanceId());
                    }
                }
            }
        }
        if (code.getBoundInstanceId() != null && !code.getBoundInstanceId().isBlank()
                && !code.getBoundInstanceId().equals(instanceId)) {
            accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                    ClientAccessLog.EVENT_DEVICE_MISMATCH);
            rateLimiter.recordFailure(ip);
            return Outcome.fail(ResultCodes.CODE_DEVICE_MISMATCH, ClientAccessLog.EVENT_DEVICE_MISMATCH);
        }

        rateLimiter.recordSuccess(ip);
        accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                ClientAccessLog.EVENT_OK);
        return Outcome.ok(code);
    }

    private Outcome failInvalid(String ip, String clientVersion, String instanceId, Long codeId, String codeName) {
        accessLog.record(codeId, codeName, ClientAccessLog.MODE_CODE, ip, clientVersion, instanceId,
                ClientAccessLog.EVENT_BAD_SIGN);
        rateLimiter.recordFailure(ip);
        return Outcome.fail(ResultCodes.CODE_INVALID, ClientAccessLog.EVENT_BAD_SIGN);
    }

    /** True when the nonce is fresh; false on replay. Trims expired entries inline. */
    private boolean registerNonce(String keyId, String nonce, long now) {
        Map<String, Long> seen = nonces.computeIfAbsent(keyId, k -> new ConcurrentHashMap<>());
        if (seen.size() >= NONCE_MAX_PER_KEY) {
            Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue() > TS_WINDOW_MS) {
                    it.remove();
                }
            }
            if (seen.size() >= NONCE_MAX_PER_KEY) {
                // Window full of fresh nonces: reject rather than grow unbounded.
                return false;
            }
        }
        return seen.putIfAbsent(nonce, now) == null;
    }

    private static Map<String, String> parseHeader(String header) {
        Map<String, String> map = new ConcurrentHashMap<>();
        if (header == null) {
            return map;
        }
        for (String part : header.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && eq < part.length() - 1) {
                map.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return map;
    }
}