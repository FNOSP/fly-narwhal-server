package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.math.BigInteger;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@ControllerAdvice
@Component
public class ResultEncryptionAdvice implements ResponseBodyAdvice<Object> {
    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final int AES_KEY_LEN = 32;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte ENC_VERSION = 1;
    private static final String RESP_INFO_PREFIX = "flynarwhal_resp_v1";
    private static final String RESP_SALT = "flynarwhal";
    private static final int AUTH_CODE_PAYLOAD_LEN = 33;

    private final FnAuthService fnAuthService;
    private final ObjectMapper objectMapper;

    public ResultEncryptionAdvice(FnAuthService fnAuthService, ObjectMapper objectMapper) {
        this.fnAuthService = fnAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof Result<?>)) {
            return body;
        }
        @SuppressWarnings("unchecked")
        Result<Object> result = (Result<Object>) body;
        if (selectedContentType != null && MediaType.TEXT_EVENT_STREAM.includes(selectedContentType)) {
            return body;
        }
        HttpServletRequest servletRequest = unwrapServletRequest(request);
        if (servletRequest != null) {
            String path = servletRequest.getRequestURI();
            if (path != null && path.startsWith("/api/config/auth-code")) {
                return body;
            }
        }
        if (result.getEncrypted() != null && result.getEncrypted()) {
            return body;
        }
        if (result.getData() == null) {
            return body;
        }

        String authCode = fnAuthService.getResponseAuthCodeOrNull();
        if (authCode == null || authCode.isBlank()) {
            return body;
        }

        try {
            Object data = result.getData();
            String dataJson = objectMapper.writeValueAsString(data);
            if (!FnAuthService.isFn1AuthCode(authCode)) {
                return body;
            }
            if (servletRequest == null) {
                return body;
            }
            String keyx = servletRequest.getHeader("Keyx");
            if (keyx == null || keyx.isBlank()) {
                return body;
            }
            String fn1PrivateKeyBase64 = fnAuthService.getResponseFn1PrivateKeyBase64OrNull();
            if (fn1PrivateKeyBase64 == null || fn1PrivateKeyBase64.isBlank()) {
                return body;
            }

            String encrypted = ExternalAuthxVerifier.encryptResponse(dataJson, authCode, fn1PrivateKeyBase64, keyx.trim());
            if (encrypted == null || encrypted.isBlank()) {
                encrypted = encryptResponseInternal(dataJson, authCode, fn1PrivateKeyBase64, keyx.trim());
            }
            if (encrypted == null || encrypted.isBlank()) {
                return body;
            }

            result.setData(encrypted);
            result.setEncrypted(true);
            return result;
        } catch (Exception e) {
            return body;
        }
    }

    private static HttpServletRequest unwrapServletRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servlet) {
            return servlet.getServletRequest();
        }
        return null;
    }

    private static String encryptResponseInternal(String plaintextJson, String authCode, String privateKeyBase64, String keyxBase64Url) {
        try {
            byte[] serverPubRaw = parseX25519PublicRawFromAuthCode(authCode);
            byte[] serverPrivRaw = Base64.getDecoder().decode(privateKeyBase64);
            if (serverPrivRaw.length != 32) {
                return null;
            }
            PrivateKey serverPrivKey = decodeX25519PrivateKeyFromRaw32(serverPrivRaw);

            byte[] clientPubRaw = base64UrlDecodeNoPadding(keyxBase64Url);
            if (clientPubRaw.length != 32) {
                return null;
            }
            PublicKey clientPubKey = decodeX25519PublicKeyFromRaw32(clientPubRaw);

            byte[] sharedSecret = x25519SharedSecret(serverPrivKey, clientPubKey);
            byte[] info = concat(RESP_INFO_PREFIX.getBytes(StandardCharsets.UTF_8), serverPubRaw, clientPubRaw);
            byte[] aesKey = hkdfSha256(sharedSecret, RESP_SALT.getBytes(StandardCharsets.UTF_8), info, AES_KEY_LEN);

            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = plaintextJson.getBytes(StandardCharsets.UTF_8);
            byte[] ct = cipher.doFinal(pt);

            byte[] payload = new byte[1 + iv.length + ct.length];
            payload[0] = ENC_VERSION;
            System.arraycopy(iv, 0, payload, 1, iv.length);
            System.arraycopy(ct, 0, payload, 1 + iv.length, ct.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] parseX25519PublicRawFromAuthCode(String authCode) {
        int idx = authCode.indexOf('_');
        if (idx <= 0) {
            throw new IllegalArgumentException("Invalid auth code format");
        }
        String prefix = authCode.substring(0, idx);
        if (!"FN1".equals(prefix)) {
            throw new IllegalArgumentException("Unsupported auth code prefix");
        }
        String payloadB64Url = authCode.substring(idx + 1);
        byte[] payload = base64UrlDecodeNoPadding(payloadB64Url);
        if (payload.length != AUTH_CODE_PAYLOAD_LEN) {
            throw new IllegalArgumentException("Invalid auth code payload length");
        }
        if (payload[0] != ENC_VERSION) {
            throw new IllegalArgumentException("Unsupported auth code version");
        }
        byte[] pub = new byte[32];
        System.arraycopy(payload, 1, pub, 0, 32);
        return pub;
    }

    private static PrivateKey decodeX25519PrivateKeyFromRaw32(byte[] raw32) throws Exception {
        return KeyFactory.getInstance("X25519").generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, raw32));
    }

    private static PublicKey decodeX25519PublicKeyFromRaw32(byte[] raw32) throws Exception {
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = raw32[31 - i];
        }
        BigInteger u = new BigInteger(1, be);
        return KeyFactory.getInstance("X25519").generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, u));
    }

    private static byte[] x25519SharedSecret(PrivateKey privateKey, PublicKey peerPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(privateKey);
        ka.doPhase(peerPublicKey, true);
        return ka.generateSecret();
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) throws Exception {
        byte[] prk = hmacSha256(salt, ikm);
        byte[] okm = new byte[outLen];
        byte[] t = new byte[0];
        int produced = 0;
        int counter = 1;
        while (produced < outLen) {
            byte[] data = concat(t, info, new byte[]{(byte) counter});
            t = hmacSha256(prk, data);
            int take = Math.min(t.length, outLen - produced);
            System.arraycopy(t, 0, okm, produced, take);
            produced += take;
            counter++;
        }
        return okm;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] out = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        return out;
    }

    private static byte[] base64UrlDecodeNoPadding(String s) {
        int mod = s.length() % 4;
        String padded = (mod == 0) ? s : s + "====".substring(mod);
        return Base64.getUrlDecoder().decode(padded);
    }
}
