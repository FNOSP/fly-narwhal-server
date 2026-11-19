package com.jankinwu.flynarwhal.web.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Integrity pinning of the white-box verifier binary: a replaced/stub sibling must fail
 * closed. The expected SHA-256 is normally baked into the native image at build time
 * ({@link WbVerifierIntegrity}); here it is mocked to exercise the comparison logic.
 */
class ExternalWbVerifierIntegrityTest {

    private static String sha256(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    @Test
    void acceptsBinaryMatchingPin(@TempDir Path dir) throws Exception {
        Path bin = Files.write(dir.resolve("flynarwhal-wb"), new byte[]{1, 2, 3, 4, 5});
        String pin = sha256(bin);
        try (MockedStatic<WbVerifierIntegrity> m = mockStatic(WbVerifierIntegrity.class)) {
            m.when(() -> WbVerifierIntegrity.expectedSha256(anyString())).thenReturn(pin);
            assertTrue(ExternalWbVerifier.integrityOk(bin));
        }
    }

    @Test
    void rejectsMismatchedBinary(@TempDir Path dir) throws Exception {
        Path bin = Files.write(dir.resolve("flynarwhal-wb"), new byte[]{1, 2, 3, 4, 5});
        try (MockedStatic<WbVerifierIntegrity> m = mockStatic(WbVerifierIntegrity.class)) {
            m.when(() -> WbVerifierIntegrity.expectedSha256(anyString()))
                    .thenReturn("0000000000000000000000000000000000000000000000000000000000000000");
            assertFalse(ExternalWbVerifier.integrityOk(bin));
        }
    }

    @Test
    void rejectsWhenNoPinForPlatform(@TempDir Path dir) throws Exception {
        Path bin = Files.write(dir.resolve("flynarwhal-wb"), new byte[]{1, 2, 3, 4, 5});
        try (MockedStatic<WbVerifierIntegrity> m = mockStatic(WbVerifierIntegrity.class)) {
            m.when(() -> WbVerifierIntegrity.expectedSha256(anyString())).thenReturn(null);
            assertFalse(ExternalWbVerifier.integrityOk(bin));
        }
    }

    @Test
    void rejectsStubSubstitutionAfterPin(@TempDir Path dir) throws Exception {
        Path bin = Files.write(dir.resolve("flynarwhal-wb"), new byte[]{1, 2, 3, 4, 5});
        String pin = sha256(bin);
        try (MockedStatic<WbVerifierIntegrity> m = mockStatic(WbVerifierIntegrity.class)) {
            m.when(() -> WbVerifierIntegrity.expectedSha256(anyString())).thenReturn(pin);
            assertTrue(ExternalWbVerifier.integrityOk(bin));
            // Attacker replaces the genuine binary with a stub that always answers OK.
            Files.write(bin, new byte[]{9, 9, 9});
            assertFalse(ExternalWbVerifier.integrityOk(bin));
        }
    }
}