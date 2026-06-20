package io.github.absketches.runway.codegen.output;

import io.github.absketches.runway.codegen.analysis.ColumnReference;
import io.github.absketches.runway.codegen.analysis.SqlImpact;
import io.github.absketches.runway.codegen.migration.ParsedMigration;
import io.github.absketches.runway.codegen.migration.ParsedStatement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MigrationGraphWriter {
    private MigrationGraphWriter() {
    }

    public static String write(
        List<ParsedMigration> migrations,
        Map<ParsedStatement, SqlImpact> analysis
    ) {
        StringBuilder resources = new StringBuilder();
        StringBuilder impact = new StringBuilder();
        Map<String, String> resourceNodes = new LinkedHashMap<>();

        for (int migrationIndex = 0; migrationIndex < migrations.size(); migrationIndex++) {
            ParsedMigration migration = migrations.get(migrationIndex);
            String migrationNode = "migration_" + migrationIndex;
            impact.append("  ").append(migrationNode)
                .append(" [label=\"").append(escape(migrationLabel(migration))).append("\"];\n");

            for (int statementIndex = 0; statementIndex < migration.statements().size(); statementIndex++) {
                ParsedStatement statement = migration.statements().get(statementIndex);
                SqlImpact statementImpact = analysis.get(statement);
                String statementNode = migrationNode + "_statement_" + statementIndex;
                impact.append("  ").append(statementNode)
                    .append(" [label=\"").append(statementImpact.type());
                if (!statementImpact.analysisComplete()) {
                    impact.append("\\nanalysis incomplete");
                }
                impact.append("\", shape=note");
                if (!statementImpact.analysisComplete()) {
                    impact.append(", color=\"#d97706\"");
                }
                impact.append("];\n")
                    .append("  ").append(migrationNode).append(" -> ").append(statementNode)
                    .append(" [label=\"contains\", style=dotted];\n");

                addEdges(resources, impact, resourceNodes, statementImpact.readTables(), "table", statementNode, true);
                addEdges(resources, impact, resourceNodes, statementImpact.writtenTables(), "table", statementNode, false);
                addColumnEdges(resources, impact, resourceNodes, statementImpact.readColumns(), statementNode, true);
                addColumnEdges(resources, impact, resourceNodes, statementImpact.writtenColumns(), statementNode, false);

                if (!statementImpact.schemaObject().isEmpty()) {
                    String objectNode = resourceNode(resources, resourceNodes, "object", statementImpact.schemaObject());
                    impact.append("  ").append(statementNode).append(" -> ").append(objectNode)
                        .append(" [label=\"").append(statementImpact.type()).append("\"];\n");
                }
            }
        }

        return """
            digraph runway_impact {
              rankdir=LR;
              node [shape=box];
            %s%s}
            """.formatted(resources, impact);
    }

    private static void addColumnEdges(
        StringBuilder resources,
        StringBuilder impact,
        Map<String, String> resourceNodes,
        List<ColumnReference> columns,
        String statementNode,
        boolean read
    ) {
        addEdges(
            resources,
            impact,
            resourceNodes,
            columns.stream().map(ColumnReference::qualifiedName).toList(),
            "column",
            statementNode,
            read
        );
    }

    private static void addEdges(
        StringBuilder resources,
        StringBuilder impact,
        Map<String, String> resourceNodes,
        List<String> names,
        String kind,
        String statementNode,
        boolean read
    ) {
        for (String name : names) {
            String resourceNode = resourceNode(resources, resourceNodes, kind, name);
            if (read) {
                impact.append("  ").append(resourceNode).append(" -> ").append(statementNode)
                    .append(" [label=\"reads\", color=\"#2563eb\"];\n");
            } else {
                impact.append("  ").append(statementNode).append(" -> ").append(resourceNode)
                    .append(" [label=\"writes\", color=\"#dc2626\"];\n");
            }
        }
    }

    private static String resourceNode(
        StringBuilder resources,
        Map<String, String> resourceNodes,
        String kind,
        String name
    ) {
        String key = kind + ":" + name;
        String existing = resourceNodes.get(key);
        if (existing != null) {
            return existing;
        }
        String node = "resource_" + resourceNodes.size();
        resourceNodes.put(key, node);
        resources.append("  ").append(node)
            .append(" [label=\"").append(kind).append(" ").append(escape(name))
            .append("\", shape=ellipse];\n");
        return node;
    }

    private static String migrationLabel(ParsedMigration migration) {
        return "V" + migration.version() + " " + migration.description();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
