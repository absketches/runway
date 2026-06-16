package io.github.absketches.runway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MigrationVersion implements Comparable<MigrationVersion> {
    private final String value;
    private final List<Integer> parts;

    private MigrationVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Migration version must not be blank");
        }
        this.value = value;
        this.parts = parseParts(value);
    }

    public static MigrationVersion of(String value) {
        return new MigrationVersion(value);
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(MigrationVersion other) {
        int max = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < max; i++) {
            int left = i < parts.size() ? parts.get(i) : 0;
            int right = i < other.parts.size() ? other.parts.get(i) : 0;
            int compared = Integer.compare(left, right);
            if (compared != 0) {
                return compared;
            }
        }
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MigrationVersion that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static List<Integer> parseParts(String value) {
        String[] tokens = value.split("[._-]");
        List<Integer> parsed = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token.isBlank() || !token.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Migration version must contain numeric parts only: " + value);
            }
            parsed.add(Integer.parseInt(token));
        }
        return List.copyOf(parsed);
    }
}
