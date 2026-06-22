package io.github.absketches.runway.codegen.sql.split;

enum SqlScannerState {
    NORMAL,
    SINGLE_QUOTE,
    DOUBLE_QUOTE,
    BACKTICK,
    BRACKET,
    LINE_COMMENT,
    BLOCK_COMMENT,
    DOLLAR_QUOTE
}
