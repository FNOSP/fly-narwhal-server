package com.jankinwu.flynarwhal.web.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Key derivation for the per-client code protocol (FNK2).
 *
 * <p>The code is {@code FNK2_<base64url(32 random bytes)>}. Two one-way
 * derivations are stored server-side: {@code SHA-256(full code string)} as the
 * lookup key, and {@code HKDF-SHA256(secret, "flynarwhal", "flynarwhal_sign_v2")}
 * as the HMAC signing key. The plaintext code is never persisted.
 *
 * <p>Response encryption (v2 envelope) derives its AES key from the same sign
 * key with info {@code "flynarwhal_resp_v2" || keyx32}.
 */
public final class ClientCodeCrypto {

    public static final String CODE_PREFIX = "FNK2_";
    public static final int SECRET_LEN = 32;
    public static final String HKDF_SALT = "flynarwhal";
    public static final String SIGN_INFO = "flynarwhal_sign_v2";
    public static final String RESP_INFO_PREFIX = "flynarwhal_resp_v2";

    private ClientCodeCrypto() {
    }

    public static boolean isFnk2Code(String code) {
        return code != null && code.startsWith(CODE_PREFIX);
    }

    /** Decodes the 32-byte secret from a full FNK2 code, or null when malformed. */
    public static byte[] secretBytes(String code) {
        if (!isFnk2Code(code)) {
            return null;
        }
        try {
            byte[] secret = Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length()));
            return secret.length == SECRET_LEN ? secret : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String buildCode(byte[] secret32) {
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret32);
    }

    /** Lookup key: SHA-256 hex of the full code string (UTF-8). */
    public static String keyIdHex(String fullCode) {
        return AuthCanonicalization.sha256Hex(fullCode);
    }

    /** HMAC signing key derived from the code secret. */
    public static byte[] deriveSignKey(byte[] secret32) {
        return hkdfSha256(secret32, HKDF_SALT.getBytes(StandardCharsets.UTF_8),
                SIGN_INFO.getBytes(StandardCharsets.UTF_8), 32);
    }

    /** AES-256 key for the v2 response envelope. */
    public static byte[] deriveResponseKey(byte[] signKey, byte[] keyx32) {
        byte[] prefix = RESP_INFO_PREFIX.getBytes(StandardCharsets.UTF_8);
        byte[] info = new byte[prefix.length + keyx32.length];
        System.arraycopy(prefix, 0, info, 0, prefix.length);
        System.arraycopy(keyx32, 0, info, prefix.length, keyx32.length);
        return hkdfSha256(signKey, HKDF_SALT.getBytes(StandardCharsets.UTF_8), info, 32);
    }

    /**
     * Request signature: HMAC-SHA256 over
     * {@code FNK2_method_url_ts_nonce_dataJsonMd5_deviceId}, hex-encoded.
     */
    public static String requestSignature(byte[] signKey, String method, String url,
                                          String ts, String nonce, String dataJsonMd5, String deviceId) {
        String message = String.join("_", "FNK2", method, url, ts, nonce, dataJsonMd5, deviceId == null ? "" : deviceId);
        return hmacSha256Hex(signKey, message.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacSha256Hex(byte[] key, byte[] data) {
        byte[] mac = hmacSha256(key, data);
        StringBuilder sb = new StringBuilder(mac.length * 2);
        for (byte b : mac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Constant-time hex-string comparison. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) {
        byte[] prk = hmacSha256(salt, ikm);
        byte[] okm = new byte[outLen];
        byte[] t = new byte[0];
        int produced = 0;
        int counter = 1;
        while (produced < outLen) {
            byte[] data = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, data, 0, t.length);
            System.arraycopy(info, 0, data, t.length, info.length);
            data[data.length - 1] = (byte) counter;
            t = hmacSha256(prk, data);
            int take = Math.min(t.length, outLen - produced);
            System.arraycopy(t, 0, okm, produced, take);
            produced += take;
            counter++;
        }
        return okm;
    }
}