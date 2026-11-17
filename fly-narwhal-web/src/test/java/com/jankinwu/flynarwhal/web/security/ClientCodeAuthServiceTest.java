package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.core.dto.response.ResultCodes;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import com.jankinwu.flynarwhal.web.service.ClientAccessLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientCodeAuthServiceTest {

    private ClientAuthCodeMapper mapper;
    private ClientAccessLogService accessLog;
    private AuthRateLimiter limiter;
    private ClientCodeAuthService service;

    private ClientAuthCode stored;
    private String fullCode;
    private byte[] signKey;

    @BeforeEach
    void setUp() {
        mapper = mock(ClientAuthCodeMapper.class);
        accessLog = mock(ClientAccessLogService.class);
        limiter = new AuthRateLimiter();
        service = new ClientCodeAuthService(mapper, accessLog, limiter);

        fullCode = ClientCodeCrypto.buildCode(ClientCodeCryptoTest.SECRET);
        signKey = ClientCodeCrypto.deriveSignKey(ClientCodeCryptoTest.SECRET);
        stored = new ClientAuthCode();
        stored.setId(1L);
        stored.setName("test client");
        stored.setCodeHash(ClientCodeCrypto.keyIdHex(fullCode));
        stored.setSignKey(Base64.getEncoder().encodeToString(signKey));
        stored.setStatus(ClientAuthCode.STATUS_ACTIVE);
        stored.setScope(ClientAuthCode.SCOPE_READ_WRITE);
        when(mapper.selectOne(any())).thenReturn(stored);
        when(mapper.update(any(), any())).thenReturn(1);
    }

    private String header(String ts, String nonce, String method, String url, String deviceId) {
        String sig = ClientCodeCrypto.requestSignature(signKey, method, url, ts, nonce,
                AuthCanonicalization.dataJsonMd5(null, null), deviceId);
        return "keyid=" + stored.getCodeHash() + "&ts=" + ts + "&nonce=" + nonce + "&sig=" + sig;
    }

    private ClientCodeAuthService.Outcome verify(String header, String deviceId) {
        return service.verify(header, "GET", "/api/danmu/get", new HashMap<>(), null,
                deviceId, "10.0.0.1", "2.4.0");
    }

    @Test
    void acceptsValidSignatureAndBindsDeviceOnFirstUse() {
        String ts = Long.toString(System.currentTimeMillis());
        String nonce = "0123456789abcdef0123456789abcdef";
        ClientCodeAuthService.Outcome outcome = verify(header(ts, nonce, "GET", "/api/danmu/get", "device-a"), "device-a");
        assertTrue(outcome.ok());
        assertEquals("device-a", outcome.code().getBoundDeviceId());
    }

    @Test
    void rejectsWrongSignature() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "device-a");
        char last = h.charAt(h.length() - 1);
        h = h.substring(0, h.length() - 1) + (last == '0' ? '1' : '0');
        ClientCodeAuthService.Outcome outcome = verify(h, "device-a");
        assertFalse(outcome.ok());
        assertEquals(ResultCodes.CODE_INVALID, outcome.errorCode());
    }

    @Test
    void rejectsExpiredTimestamp() {
        String ts = Long.toString(System.currentTimeMillis() - 6 * 60_000);
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_REPLAYED, outcome.errorCode());
    }

    @Test
    void rejectsReplayedNonce() {
        String ts = Long.toString(System.currentTimeMillis());
        String nonce = "fedcba9876543210fedcba9876543210";
        assertTrue(verify(header(ts, nonce, "GET", "/api/danmu/get", "d"), "d").ok());
        ClientCodeAuthService.Outcome second = verify(header(ts, nonce, "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_REPLAYED, second.errorCode());
    }

    @Test
    void rejectsDisabledCode() {
        stored.setStatus(ClientAuthCode.STATUS_DISABLED);
        String ts = Long.toString(System.currentTimeMillis());
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_DISABLED, outcome.errorCode());
    }

    @Test
    void rejectsExpiredCode() {
        stored.setExpireTime(LocalDateTime.now().minusDays(1));
        String ts = Long.toString(System.currentTimeMillis());
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_EXPIRED, outcome.errorCode());
    }

    @Test
    void permanentCodeNeverExpires() {
        stored.setExpireTime(null);
        String ts = Long.toString(System.currentTimeMillis());
        assertTrue(verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d"), "d").ok());
    }

    @Test
    void rejectsMismatchedDeviceAfterBinding() {
        stored.setBoundDeviceId("device-a");
        String ts = Long.toString(System.currentTimeMillis());
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "device-b"), "device-b");
        assertEquals(ResultCodes.CODE_DEVICE_MISMATCH, outcome.errorCode());
    }

    @Test
    void unknownKeyIdIsInvalid() {
        when(mapper.selectOne(any())).thenReturn(null);
        String ts = Long.toString(System.currentTimeMillis());
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_INVALID, outcome.errorCode());
    }

    @Test
    void malformedHeaderIsInvalid() {
        assertEquals(ResultCodes.CODE_INVALID, verify("garbage", "d").errorCode());
        assertEquals(ResultCodes.CODE_INVALID, verify(null, "d").errorCode());
    }

    @Test
    void rateLimiterBlocksAfterRepeatedFailures() {
        String ts = Long.toString(System.currentTimeMillis());
        for (int i = 0; i < 20; i++) {
            String nonce = String.format("%032x", i);
            // Flip the last hex char deterministically (sig=0 would be a no-op
            // for the ~1/16 of signatures already ending in 0).
            String bad = header(ts, nonce, "GET", "/api/danmu/get", "d");
            char last = bad.charAt(bad.length() - 1);
            bad = bad.substring(0, bad.length() - 1) + (last == '0' ? '1' : '0');
            verify(bad, "d");
        }
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "GET", "/api/danmu/get", "d"), "d");
        assertEquals(ResultCodes.CODE_RATE_LIMITED, outcome.errorCode());
    }

    @Test
    void createCodeStoresOnlyDerivations() {
        ArgumentCaptor<ClientAuthCode> captor = ArgumentCaptor.forClass(ClientAuthCode.class);
        when(mapper.insert(captor.capture())).thenReturn(1);
        ClientCodeAuthService.CreatedCode created = service.createCode("my client", 30, ClientAuthCode.SCOPE_READ_ONLY);
        assertNotNull(created.code());
        assertTrue(created.code().startsWith("FNK2_"));
        ClientAuthCode row = captor.getValue();
        assertEquals(ClientCodeCrypto.keyIdHex(created.code()), row.getCodeHash());
        assertFalse(row.getSignKey().contains(created.code()));
        assertEquals(ClientAuthCode.SCOPE_READ_ONLY, row.getScope());
        assertNotNull(row.getExpireTime());
        // The stored sign key must verify signatures made with the plaintext code.
        byte[] storedKey = Base64.getDecoder().decode(row.getSignKey());
        byte[] fromCode = ClientCodeCrypto.deriveSignKey(ClientCodeCrypto.secretBytes(created.code()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(fromCode, storedKey);
    }

    @Test
    void createCodePermanentHasNullExpiry() {
        when(mapper.insert(any(ClientAuthCode.class))).thenReturn(1);
        ClientCodeAuthService.CreatedCode created = service.createCode("perm", null, null);
        org.junit.jupiter.api.Assertions.assertNull(created.expireTime());
    }
}