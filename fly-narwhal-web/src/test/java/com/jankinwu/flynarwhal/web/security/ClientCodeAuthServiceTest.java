package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.core.dto.response.ResultCodes;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import com.jankinwu.flynarwhal.web.service.ClientAccessLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

    private String header(String ts, String nonce, String method, String url, String instanceId) {
        String sig = ClientCodeCrypto.requestSignature(signKey, method, url, ts, nonce,
                AuthCanonicalization.dataJsonMd5(null, null), instanceId);
        return "keyid=" + stored.getCodeHash() + "&ts=" + ts + "&nonce=" + nonce + "&sig=" + sig;
    }

    private ClientCodeAuthService.Outcome verify(String header, String instanceId) {
        return verify(header, null, instanceId);
    }

    private ClientCodeAuthService.Outcome verify(String header, String fnwb, String instanceId) {
        return service.verify(header, fnwb, "GET", "/api/danmu/get", new HashMap<>(), null,
                instanceId, "10.0.0.1", "2.4.0");
    }

    @Test
    void acceptsValidSignatureAndBindsDeviceOnFirstUse() {
        String ts = Long.toString(System.currentTimeMillis());
        String nonce = "0123456789abcdef0123456789abcdef";
        ClientCodeAuthService.Outcome outcome = verify(header(ts, nonce, "GET", "/api/danmu/get", "macos-arm64-fn-aaaaaaaaaaaaaaaa"), "macos-arm64-fn-aaaaaaaaaaaaaaaa");
        assertTrue(outcome.ok());
        assertEquals("macos-arm64-fn-aaaaaaaaaaaaaaaa", outcome.code().getBoundInstanceId());
    }

    @Test
    void rejectsWrongSignature() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "macos-arm64-fn-aaaaaaaaaaaaaaaa");
        char last = h.charAt(h.length() - 1);
        h = h.substring(0, h.length() - 1) + (last == '0' ? '1' : '0');
        ClientCodeAuthService.Outcome outcome = verify(h, "macos-arm64-fn-aaaaaaaaaaaaaaaa");
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
        stored.setBoundInstanceId("macos-arm64-fn-aaaaaaaaaaaaaaaa");
        String ts = Long.toString(System.currentTimeMillis());
        ClientCodeAuthService.Outcome outcome = verify(header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "macos-arm64-fn-bbbbbbbbbbbbbbbb"), "macos-arm64-fn-bbbbbbbbbbbbbbbb");
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

    // ---- V2 white-box build proof (Fnwb / 4108) ----

    private static final String VALID_WB = "v=2&sig=0123456789abcdef0123456789abcdef";

    @Test
    void releaseAcceptsValidBuildProof() {
        String ts = Long.toString(System.currentTimeMillis());
        String nonce = "0123456789abcdef0123456789abcdef";
        String instance = "macos-arm64-fn-aaaaaaaaaaaaaaaa";
        String h = header(ts, nonce, "GET", "/api/danmu/get", instance);
        String fnauthSig = h.substring(h.lastIndexOf('=') + 1);
        String dataMd5 = AuthCanonicalization.dataJsonMd5(new HashMap<>(), null);
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Boolean.TRUE);
            ClientCodeAuthService.Outcome outcome = verify(h, VALID_WB, instance);
            assertTrue(outcome.ok());
            // The daemon must receive the exact canonical fields, with the raw Fnauth sig.
            wb.verify(() -> ExternalWbVerifier.verify(
                    "GET", "/api/danmu/get", ts, nonce, dataMd5, instance, fnauthSig,
                    "0123456789abcdef0123456789abcdef"));
        }
    }

    @Test
    void releaseRejectsMissingBuildProof() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            ClientCodeAuthService.Outcome outcome = verify(h, null, "d");
            assertEquals(ResultCodes.CODE_BUILD_REQUIRED, outcome.errorCode());
            assertEquals(ClientAccessLog.EVENT_BUILD_PROOF_FAILED, outcome.event());
            // A missing header must not even reach the daemon.
            wb.verify(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()),
                    never());
        }
    }

    @Test
    void releaseRejectsMalformedBuildProof() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        String[] malformed = {
                "garbage",
                "v=1&sig=0123456789abcdef0123456789abcdef",
                "v=2&sig=0123456789ABCDEF0123456789ABCDEF",
                "v=2&sig=0123456789abcdef",
                "v=2&sig=0123456789abcdef0123456789abcdef0",
                "sig=0123456789abcdef0123456789abcdef",
                "v=2",
        };
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            for (String fnwb : malformed) {
                assertEquals(ResultCodes.CODE_BUILD_REQUIRED, verify(h, fnwb, "d").errorCode(),
                        "expected 4108 for: " + fnwb);
            }
            wb.verify(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()),
                    never());
        }
    }

    @Test
    void releaseRejectsWhenDaemonSaysFail() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Boolean.FALSE);
            assertEquals(ResultCodes.CODE_BUILD_REQUIRED, verify(h, VALID_WB, "d").errorCode());
        }
    }

    @Test
    void releaseRejectsWhenDaemonUnavailable() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            // Fail-closed: null (execution error / timeout / missing binary) rejects.
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(null);
            assertEquals(ResultCodes.CODE_BUILD_REQUIRED, verify(h, VALID_WB, "d").errorCode());
        }
    }

    @Test
    void devSkipsBuildProofEntirely() {
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(false);
            assertTrue(verify(h, null, "d").ok());
            wb.verify(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()),
                    never());
        }
    }

    @Test
    void buildProofFailureConsumesNoNonceAndBindsNoInstance() {
        String ts = Long.toString(System.currentTimeMillis());
        String nonce = "0123456789abcdef0123456789abcdef";
        String h = header(ts, nonce, "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Boolean.FALSE);
            assertEquals(ResultCodes.CODE_BUILD_REQUIRED, verify(h, VALID_WB, "d").errorCode());

            // No first-use binding UPDATE, no bound instance on the in-memory row.
            org.mockito.Mockito.verify(mapper, never()).update(any(), any());
            assertNull(stored.getBoundInstanceId());
            org.mockito.Mockito.verify(accessLog).record(eq(1L), eq("test client"), eq(ClientAccessLog.MODE_CODE),
                    eq("10.0.0.1"), eq("2.4.0"), eq("d"), eq(ClientAccessLog.EVENT_BUILD_PROOF_FAILED));

            // The nonce was not consumed: the same request succeeds once the proof passes.
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Boolean.TRUE);
            assertTrue(verify(h, VALID_WB, "d").ok());
        }
    }

    @Test
    void buildProofFailureRecordsRateLimiterFailure() {
        AuthRateLimiter mockLimiter = mock(AuthRateLimiter.class);
        ClientCodeAuthService svc = new ClientCodeAuthService(mapper, accessLog, mockLimiter);
        String ts = Long.toString(System.currentTimeMillis());
        String h = header(ts, "0123456789abcdef0123456789abcdef", "GET", "/api/danmu/get", "d");
        try (MockedStatic<ExternalWbVerifier> wb = mockStatic(ExternalWbVerifier.class)) {
            wb.when(ExternalWbVerifier::isExternalOnlyMode).thenReturn(true);
            wb.when(() -> ExternalWbVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Boolean.FALSE);
            ClientCodeAuthService.Outcome outcome = svc.verify(h, VALID_WB, "GET", "/api/danmu/get",
                    new HashMap<>(), null, "d", "10.0.0.1", "2.4.0");
            assertEquals(ResultCodes.CODE_BUILD_REQUIRED, outcome.errorCode());
            org.mockito.Mockito.verify(mockLimiter).recordFailure("10.0.0.1");
            org.mockito.Mockito.verify(mockLimiter, never()).recordSuccess(anyString());
        }
    }
}