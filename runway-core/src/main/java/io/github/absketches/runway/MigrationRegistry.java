package io.github.absketches.runway;

import java.util.List;

public interface MigrationRegistry {
    List<MigrationDefinition> migrations();
}
