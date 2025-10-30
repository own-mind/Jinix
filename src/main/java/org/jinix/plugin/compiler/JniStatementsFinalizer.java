package org.jinix.plugin.compiler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.jinix.plugin.compiler.CPPTranspiler.CPPStatement;

public class JniStatementsFinalizer {
    /**
     * Inserts JniStatements into the code blocks and optimizes them
     */
    public void finalize(List<CPPStatement> statements) {
        traverse(statements, null, null, this::expand);
        traverse(statements, null, null, this::collapse);
    }

    private void collapse(List<CPPStatement> block, @Nullable List<CPPStatement> parent, List<List<CPPStatement>> siblings) {
        if (parent == null) return;
        var jniList = block.stream().flatMap(s -> s instanceof CPPTranspiler.JniStatement j ? Stream.of(j) : Stream.empty()).toList();

        for (CPPTranspiler.JniStatement jniStatement : jniList) {
            if (parent.contains(jniStatement)) {
                block.remove(jniStatement);
            } else if (siblings.stream().anyMatch(s -> s.contains(jniStatement))) {
                siblings.forEach(s -> s.remove(jniStatement));
                parent.add(jniStatement);
            }
        }
    }

    private void expand(List<CPPStatement> block, @Nullable List<CPPStatement> parent, List<List<CPPStatement>> siblings) {
        LinkedHashSet<CPPTranspiler.JniStatement> statements = new LinkedHashSet<>();

        for (CPPStatement statement : block) {
            statements.addAll(statement.jniStatements);
        }

        // jniStatements are always on top
        var statementsList = new ArrayList<>(statements);
        for (int i = statementsList.size() - 1; i >= 0; i--) {
            block.addFirst(statementsList.get(i));
        }
    }

    private void traverse(List<CPPStatement> block, @Nullable List<CPPStatement> parent, CPPStatement parentStatement, TraverseConsumer consumer) {
        for (CPPStatement statement : block) {
            if (statement.blockType != null && !statement.blocks.isEmpty()) {
                statement.blocks.forEach(b -> traverse(b, block, statement, consumer));
            }
        }

        if (parent == null) {
            consumer.apply(block, null, List.of());
        } else {
            var siblings = new ArrayList<>(parentStatement.blocks);
            siblings.remove(block);
            consumer.apply(block, parent, siblings);
        }
    }

    @FunctionalInterface
    private interface TraverseConsumer {
        void apply(List<CPPStatement> block, @Nullable List<CPPStatement> parent, List<List<CPPStatement>> siblings);
    }
}
