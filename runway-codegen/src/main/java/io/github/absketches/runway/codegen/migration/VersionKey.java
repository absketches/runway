package io.github.absketches.runway.codegen.migration;

import java.util.Arrays;
import java.util.List;

public record VersionKey(
    String value,
    List<Integer> parts
) implements Comparable<VersionKey> {
    public static VersionKey parse(String value) {
        return new VersionKey(
            value,
            Arrays.stream(value.split("[._-]"))
                .map(Integer::parseInt)
                .toList()
        );
    }

    @Override
    public int compareTo(VersionKey other) {
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
}
