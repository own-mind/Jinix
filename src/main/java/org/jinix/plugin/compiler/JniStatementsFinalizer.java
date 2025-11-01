package org.jinix.plugin.compiler;

import org.jinix.plugin.compiler.CPPTranspiler.JniStatement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jinix.plugin.compiler.CPPTranspiler.BlockType.IF;
import static org.jinix.plugin.compiler.CPPTranspiler.BlockType.SWITCH;
import static org.jinix.plugin.compiler.CPPTranspiler.CPPStatement;

public class JniStatementsFinalizer {
    /**
     * Inserts JniStatements into the code blocks and optimizes them
     */
    public void finalize(List<CPPStatement> statements) {
        traverse(statements, new ArrayList<>(), null, this::expand);

        // TODO Loop here is not necessary, but great to have for now
        var complete = new AtomicBoolean(false);
        while (!complete.get()) {
            complete.set(true);
            traverse(statements, new ArrayList<>(), null, (block, parents, parentStatement, siblings) -> {
                var changed = collapse(block, parents, parentStatement, siblings);
                if (changed) complete.set(false);
            });
        }

        traverse(statements, new ArrayList<>(), null, this::sortJni);
    }

    private void sortJni(List<CPPStatement> block, List<List<CPPStatement>> parents, CPPStatement parentStatement, List<List<CPPStatement>> siblings) {
        List<JniStatement> jniStatements = new ArrayList<>();
        for (CPPStatement statement : block) {
            if (statement instanceof JniStatement jni)
                jniStatements.add(jni);
            else
                break; // Assuming all jni statements are on top
        }
        if (jniStatements.size() <= 1) return;

        jniStatements.sort((a, b) -> {
            boolean aRequiresB = dependsOn(a, b);
            boolean bRequiresA = dependsOn(b, a);

            if (aRequiresB && bRequiresA)
                throw new IllegalStateException("Circular dependency detected between JNI statements");

            if (aRequiresB) return 1;
            if (bRequiresA) return -1;
            return 0;
        });

        for (int i = 0; i < jniStatements.size(); i++) {
            block.set(i, jniStatements.get(i));
        }
    }

    private boolean dependsOn(JniStatement stmt, JniStatement dependency) {
        return stmt.code.matches(".*\\b" + Pattern.quote(dependency.resultingVar) + "\\b.*");
    }

    private boolean collapse(List<CPPStatement> block, List<List<CPPStatement>> parents, CPPStatement parentStatement, List<List<CPPStatement>> siblings) {
        if (parents.isEmpty()) return false;
        var jniList = block.stream().flatMap(s -> s instanceof JniStatement j ? Stream.of(j) : Stream.empty()).toList();
        if (parentStatement.blockType != IF && parentStatement.blockType != SWITCH){
            jniList.forEach(parents.getLast()::addFirst);   // Loops and others considered to be non-controlling logical blocks
            block.removeAll(jniList);
            return !jniList.isEmpty();
        }

        boolean changed = false;
        for (JniStatement jniStatement : jniList) {
            if (parents.stream().anyMatch(p -> p.contains(jniStatement))) {
                block.remove(jniStatement);
                changed = true;
            } else if (siblings.stream().anyMatch(s -> s.contains(jniStatement))) {
                block.remove(jniStatement);
                siblings.forEach(s -> s.remove(jniStatement));
                parents.getLast().addFirst(jniStatement);
                changed = true;
            }
        }
        return changed;
    }

    private void expand(List<CPPStatement> block, List<List<CPPStatement>> parents, CPPStatement parentStatement,
                        List<List<CPPStatement>> siblings) {
        LinkedHashSet<JniStatement> statements = new LinkedHashSet<>();

        for (CPPStatement statement : block) {
            statements.addAll(statement.jniStatements);
        }

        // jniStatements are always on top
        var statementsList = new ArrayList<>(statements);
        for (int i = statementsList.size() - 1; i >= 0; i--) {
            block.addFirst(statementsList.get(i));
        }
    }

    private void traverse(List<CPPStatement> block, List<List<CPPStatement>> parents, CPPStatement parentStatement, TraverseConsumer consumer) {
        parents.add(block);
        for (CPPStatement statement : new ArrayList<>(block)) {
            if (statement.blockType != null && !statement.blocks.isEmpty()) {
                statement.blocks.forEach(b -> traverse(b, parents, statement, consumer));
            }
        }
        parents.removeLast();

        if (parents.isEmpty()) {
            consumer.apply(block, parents, parentStatement, List.of());
        } else {
            var siblings = new ArrayList<>(parentStatement.blocks);
            siblings.remove(block);
            consumer.apply(block, parents, parentStatement, siblings);
        }
    }

    @FunctionalInterface
    private interface TraverseConsumer {
        void apply(List<CPPStatement> block, List<List<CPPStatement>> parents, CPPStatement parentStatement, List<List<CPPStatement>> siblings);
    }
}
