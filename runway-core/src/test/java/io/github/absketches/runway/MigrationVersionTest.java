package io.github.absketches.runway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationVersionTest {
    @Test
    void comparesNumericParts() {
        List<MigrationVersion> sorted = List.of("1_10", "1", "1_2", "2").stream()
            .map(MigrationVersion::of)
            .sorted()
            .toList();

        assertEquals(List.of("1", "1_2", "1_10", "2"), sorted.stream().map(MigrationVersion::value).toList());
    }

    @Test
    void rejectsNonNumericParts() {
        assertThrows(IllegalArgumentException.class, () -> MigrationVersion.of("1_alpha"));
    }
}
