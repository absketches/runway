package io.github.absketches.runway.codegen.analysis;

public enum SqlStatementType {
    CREATE_TABLE,
    ALTER_TABLE,
    DROP_TABLE,
    CREATE_INDEX,
    DROP_INDEX,
    CREATE_TRIGGER,
    DROP_TRIGGER,
    CREATE_FUNCTION,
    ALTER_FUNCTION,
    DROP_FUNCTION,
    CREATE_PROCEDURE,
    ALTER_PROCEDURE,
    DROP_PROCEDURE,
    CREATE_VIEW,
    DROP_VIEW,
    INSERT,
    UPDATE,
    DELETE,
    UNKNOWN;

    public boolean isDml() {
        return this == INSERT || this == UPDATE || this == DELETE;
    }
}
