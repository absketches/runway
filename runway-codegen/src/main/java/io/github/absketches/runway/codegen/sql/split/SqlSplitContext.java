package io.github.absketches.runway.codegen.sql.split;

import io.github.absketches.runway.codegen.sql.dialects.SqlDialectRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SqlSplitContext {
    private static final List<String> NON_TRIGGER_CREATE_OBJECTS = List.of(
        "database",
        "event",
        "function",
        "index",
        "procedure",
        "schema",
        "sequence",
        "table",
        "type",
        "view"
    );

    private final SqlDialectRules rules;
    private final StringBuilder word = new StringBuilder();
    private final List<String> leadingWords = new ArrayList<>();
    private String delimiter = ";";
    private boolean atLineStart = true;
    private boolean compoundTrigger = false;
    private int compoundBlockDepth = 0;

    SqlSplitContext(SqlDialectRules rules) {
        this.rules = rules;
    }

    String delimiter() {
        return delimiter;
    }

    void delimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    boolean atLineStart() {
        return atLineStart;
    }

    void atLineStart(boolean atLineStart) {
        this.atLineStart = atLineStart;
    }

    void appendWord(char value) {
        word.append(value);
    }

    void finishWord() {
        if (word.isEmpty()) {
            return;
        }
        String value = word.toString().toLowerCase(Locale.ROOT);
        word.setLength(0);
        if (leadingWords.size() < 8) {
            leadingWords.add(value);
            compoundTrigger = isCompoundTrigger(leadingWords);
        }
        if (compoundTrigger) {
            if ("begin".equals(value) || "case".equals(value)) {
                compoundBlockDepth++;
            } else if ("end".equals(value) && compoundBlockDepth > 0) {
                compoundBlockDepth--;
            }
        }
    }

    boolean canTerminate() {
        return !compoundTrigger || compoundBlockDepth == 0;
    }

    void resetStatement() {
        word.setLength(0);
        leadingWords.clear();
        compoundTrigger = false;
        compoundBlockDepth = 0;
    }

    private boolean isCompoundTrigger(List<String> words) {
        if (!rules.supportsCompoundTriggerBlock()) {
            return false;
        }
        if (words.isEmpty() || !"create".equals(words.getFirst())) {
            return false;
        }
        for (int index = 1; index < words.size(); index++) {
            String value = words.get(index);
            if ("trigger".equals(value)) {
                return true;
            }
            if (NON_TRIGGER_CREATE_OBJECTS.contains(value)) {
                return false;
            }
        }
        return false;
    }
}
