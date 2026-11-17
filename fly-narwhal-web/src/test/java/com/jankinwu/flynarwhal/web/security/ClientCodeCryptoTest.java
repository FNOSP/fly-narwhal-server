package com.jankinwu.flynarwhal.web.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixed test vectors shared with the Flutter client: both sides must derive
 * the same keyid/sign key/signature from the same code.
 */
class ClientCodeCryptoTest {

    /** Deterministic 32-byte secret: 0x00..0x1f. */
    static final byte[] SECRET = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            SECRET[i] = (byte) i;
        }
    }

    static final String CODE = ClientCodeCrypto.buildCode(SECRET);

    @Test
    void codeRoundTrips() {
        assertTrue(ClientCodeCrypto.isFnk2Code(CODE));
        assertArrayEquals(SECRET, ClientCodeCrypto.secretBytes(CODE));
        assertNull(ClientCodeCrypto.secretBytes("FN1_whatever"));
        assertNull(ClientCodeCrypto.secretBytes("FNK2_not-base64!!"));
        assertNull(ClientCodeCrypto.secretBytes("FNK2_" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])));
    }

    @Test
    void keyIdIsSha256OfFullCode() {
        String expected = AuthCanonicalization.sha256Hex(CODE);
        assertEquals(expected, ClientCodeCrypto.keyIdHex(CODE));
        assertEquals(64, expected.length());
    }

    @Test
    void signKeyDerivationIsStable() {
        byte[] a = ClientCodeCrypto.deriveSignKey(SECRET);
        byte[] b = ClientCodeCrypto.deriveSignKey(SECRET);
        assertArrayEquals(a, b);
        assertEquals(32, a.length);
        // Different info must give a different key than the response derivation.
        byte[] resp = ClientCodeCrypto.deriveResponseKey(a, SECRET);
        assertFalse(java.util.Arrays.equals(a, resp));
    }

    @Test
    void requestSignatureMatchesKnownVector() {
        byte[] signKey = ClientCodeCrypto.deriveSignKey(SECRET);
        String sig = ClientCodeCrypto.requestSignature(signKey, "GET", "/api/danmu/get",
                "1700000000000", "0123456789abcdef0123456789abcdef",
                AuthCanonicalization.md5Hex(""), "flutter_test-device");
        // Independently computed HMAC over the joined message.
        String message = "FNK2_GET_/api/danmu/get_1700000000000_0123456789abcdef0123456789abcdef_"
                + AuthCanonicalization.md5Hex("") + "_flutter_test-device";
        String expected = ClientCodeCrypto.hmacSha256Hex(signKey, message.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, sig);
        assertEquals(64, sig.length());
    }

    @Test
    void constantTimeEqualsHandlesNulls() {
        assertTrue(ClientCodeCrypto.constantTimeEquals("abc", "abc"));
        assertFalse(ClientCodeCrypto.constantTimeEquals("abc", "abd"));
        assertFalse(ClientCodeCrypto.constantTimeEquals(null, "abc"));
        assertFalse(ClientCodeCrypto.constantTimeEquals("abc", null));
    }

    @Test
    void hkdfMatchesRfc5869Shape() {
        // RFC 5869 test case 1 (SHA-256).
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c};
        byte[] info = new byte[]{(byte) 0xf0, (byte) 0xf1, (byte) 0xf2, (byte) 0xf3, (byte) 0xf4,
                (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8, (byte) 0xf9};
        byte[] okm = ClientCodeCrypto.hkdfSha256(ikm, salt, info, 42);
        String hex = java.util.HexFormat.of().formatHex(okm);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", hex);
    }
}