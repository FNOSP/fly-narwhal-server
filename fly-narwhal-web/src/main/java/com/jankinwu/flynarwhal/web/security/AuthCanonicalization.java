package com.jankinwu.flynarwhal.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Request-body canonicalization shared by the legacy Authx/Signx path and the
 * per-client Fnauth path: MD5 of the raw JSON body when present, else MD5 of
 * the sorted first-value query string, else MD5 of the empty string.
 */
public final class AuthCanonicalization {

    private AuthCanonicalization() {
    }

    public static String dataJsonMd5(Map<String, String[]> parameters, byte[] body) {
        if (body != null && body.length > 0) {
            return md5Hex(new String(body, StandardCharsets.UTF_8));
        }
        if (parameters != null && !parameters.isEmpty()) {
            TreeMap<String, String[]> sortedParams = new TreeMap<>(parameters);
            String sortedParamsStr = sortedParams.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().length > 0)
                    .map(e -> e.getKey() + "=" + e.getValue()[0])
                    .collect(Collectors.joining("&"));
            return md5Hex(sortedParamsStr);
        }
        return md5Hex("");
    }

    public static String md5Hex(String input) {
        return digestHex("MD5", input);
    }

    public static String sha256Hex(String input) {
        return digestHex("SHA-256", input);
    }

    private static String digestHex(String algorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " algorithm is not available", e);
        }
    }
}