package io.github.absketches.runway.codegen.sql;

import io.github.absketches.runway.codegen.CodegenException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class SqlNormalizer {
    private SqlNormalizer() {
    }

    public static String normalize(byte[] bytes, String fileName) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            if (!decoded.isEmpty() && decoded.charAt(0) == '\uFEFF') {
                decoded = decoded.substring(1);
            }
            return decoded.replace("\r\n", "\n").replace('\r', '\n');
        } catch (CharacterCodingException e) {
            throw new CodegenException("Migration is not valid UTF-8: " + fileName, e);
        }
    }
}
