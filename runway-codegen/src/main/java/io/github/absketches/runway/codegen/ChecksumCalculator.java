package io.github.absketches.runway.codegen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ChecksumCalculator {
    private ChecksumCalculator() {
    }

    static String sha256(String normalizedSql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
