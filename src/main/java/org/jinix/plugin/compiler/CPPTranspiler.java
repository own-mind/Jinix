package org.jinix.plugin.compiler;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class CPPTranspiler extends Transpiler {
    private static final int INDENT_LENGTH = 4;

    //TODO
    private final Set<Include> toInclude = new HashSet<>();

    // Per transpilation:
    private int currentIndent;
    private MethodDeclaration declaration;

    @Override
    protected String transpileBody(MethodDeclaration method) {
        this.declaration = method;
        this.currentIndent = 0;

        var body = method.getBody().orElseThrow();
        return combine(body.getStatements(), this::transpileStatement);
    }

    private String transpileStatement(Statement statement) {
        return switch (statement) {
            case ExpressionStmt stmt -> transpileExpression(stmt.getExpression());
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    private String transpileExpression(Expression stmt) {
        return switch (stmt) {
            case VariableDeclarationExpr expr -> combine(expr.getVariables(), this::transpileVariableDeclaration);
            case LiteralExpr expr -> transpileLiteral(expr);
            default -> throw new IllegalStateException("Unexpected value: " + stmt);
        };
    }

    private String transpileLiteral(LiteralExpr expr) {
        return switch (expr) {
            case TextBlockLiteralExpr l -> "\"" + l.getValue().stripIndent().replace("\n", "\\\n") + "\"";
            case NullLiteralExpr l -> "nullptr";
            case BooleanLiteralExpr l -> l.toString();
            case LiteralStringValueExpr l -> l.toString();
            default -> throw new IllegalStateException("Unexpected value: " + expr);
        };
    }

    private String transpileVariableDeclaration(VariableDeclarator declarator) {
        String type = transpileType(declarator.getType());

        String name = declarator.getNameAsString();
        String value = declarator.getInitializer().map(this::transpileExpression).orElse(null);

        return value == null ? apply("%s %s;", type, name)
                : apply("%s %s = %s;", type, name, value);
    }

    private String transpileType(Type type) {
        var res = type.asString();

        if (type.isVarType() || res.equals("var")) {
            res = "auto";
        } else if (res.equals("String")) {
            include(Include.STRING);
            res = "std::string";
        } else if (!type.isPrimitiveType()){
            throw new RuntimeException("TODO");
        }

        return res;
    }

    @Override
    protected String getFileExtension() {
        return "cpp";
    }

    private <T extends Node> String combine(List<T> nodes, Function<T, String> mapper){
        return nodes.stream().map(mapper).collect(Collectors.joining("\n"));
    }

    private String apply(String s, Object... args) {
        return s.formatted(args).indent(currentIndent * INDENT_LENGTH).stripTrailing();
    }

    private void include(Include i) {
        this.toInclude.add(i);
    }

    public enum Include {
        STRING("string");

        private final String name;

        Include(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}