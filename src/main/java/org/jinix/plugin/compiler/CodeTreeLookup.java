package org.jinix.plugin.compiler;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithBody;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

class CodeTreeLookup {
    private final MethodDeclaration source;

    public CodeTreeLookup(MethodDeclaration source) {
        this.source = source;
    }

    /**
     * Looks thought code blocks and determines if the provided expression is used as a statement
     */
    public boolean expressionResultIgnored(Expression expression) {
        if (source.getBody().isEmpty()) return false;

        return traverseThroughBlocks(
                source.getBody().get().getStatements(),
                s -> s instanceof ExpressionStmt e && e.getExpression() == expression,
                (f, s) -> f || s
        );
    }

    /**
     * Goes through the tree of elements that contain other nodes, including themselves
     */
    private <T> T traverseThroughBlocks(List<Statement> nodes, Function<Statement, T> function, BiFunction<T, T, T> combiner) {
        T result = null;
        for (Statement statement : nodes) {
            T next = function.apply(statement);

            var inner = extractInnerNodes(statement, false);
            if (!inner.isEmpty()) {
                next = combiner.apply(next, traverseThroughBlocks(inner, function, combiner));
            }

            result = result == null ? next : combiner.apply(result, next);
        }

        return result;
    }

    private static @NotNull List<Statement> extractInnerNodes(Statement node, boolean includeDefault) {
        var nodes = new ArrayList<Statement>();
        switch (node) {
            case NodeWithStatements<?> ns -> nodes.addAll(ns.getStatements());
            case NodeWithBody<?> s -> nodes.addAll(extractInnerNodes(s.getBody(), true));
            case SwitchStmt s -> nodes.addAll(s.getEntries().stream().flatMap(e -> e.getStatements().stream()).toList());
            case TryStmt s -> {
                nodes.addAll(extractInnerNodes(s.getTryBlock(), true));
                nodes.addAll(s.getCatchClauses().stream().flatMap(c -> extractInnerNodes(c.getBody(), true).stream()).toList());
                s.getFinallyBlock().ifPresent(e -> nodes.addAll(extractInnerNodes(e, true)));
            }
            case IfStmt s -> {
                nodes.addAll(extractInnerNodes(s.getThenStmt(), true));
                s.getElseStmt().ifPresent(e -> nodes.addAll(extractInnerNodes(e, true)));
            }
            default -> {
                if (includeDefault) nodes.add(node);
            }
        }
        return nodes;
    }
}
