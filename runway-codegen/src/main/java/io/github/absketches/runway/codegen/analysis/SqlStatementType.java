package io.github.absketches.runway.codegen.analysis;

public enum SqlStatementType {
    CREATE_TABLE,
    ALTER_TABLE,
    DROP_TABLE,
    CREATE_INDEX,
    DROP_INDEX,
    CREATE_VIEW,
    DROP_VIEW,
    INSERT,
    UPDATE,
    DELETE,
    UNKNOWN
}
