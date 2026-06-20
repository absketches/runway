package io.github.absketches.runway.codegen.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChecksumCalculator {
    private ChecksumCalculator() {
    }

    public static String sha256(String normalizedSql) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
