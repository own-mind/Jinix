package org.jinix.plugin.compiler;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
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

    @Override
    protected String transpileBody(MethodDeclaration method) {
        this.currentIndent = 0;

        return transpileBlock(method.getBody().orElseThrow());
    }

    private String transpileBlock(BlockStmt body) {
        return combine(body.getStatements(), this::transpileStatement);
    }

    private String transpileStatement(Statement statement) {
        return switch (statement) {
            case ExpressionStmt stmt -> {
                var result = stmt(transpileExpression(stmt.getExpression()));
                yield result.endsWith(";") ? result : result + ";";
            }
            case IfStmt stmt -> transpileIf(stmt);
            case SwitchStmt stmt -> transpileSwitch(stmt);
            case WhileStmt stmt -> transpileWhile(stmt);
            case DoStmt stmt -> transpileDoWhile(stmt);
            case ForStmt stmt -> transpileFor(stmt);
            case ForEachStmt stmt -> transpileForEach(stmt);
            case BlockStmt stmt -> transpileBlock(stmt);
            case BreakStmt stmt -> transpileBreak(stmt);
            case ContinueStmt stmt -> transpileContinue(stmt);
            case ReturnStmt stmt -> transpileReturn(stmt);
            case ThrowStmt stmt -> transpileThrow(stmt);
            case EmptyStmt s -> stmt(s.toString());
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    private String transpileThrow(ThrowStmt stmt) {
        return stmt("throw %s;", transpileExpression(stmt.getExpression()));
    }

    private String transpileReturn(ReturnStmt stmt) {
        return stmt(stmt.getExpression().map(s -> stmt("return %s;", transpileExpression(s))).orElse("return;"));
    }

    private String transpileContinue(ContinueStmt stmt) {
        if (stmt.getLabel().isPresent())
            throw new IllegalArgumentException("Labeled continue statements are not supported");

        return stmt("continue;");
    }

    private String transpileBreak(BreakStmt stmt) {
        if (stmt.getLabel().isPresent())
            throw new IllegalArgumentException("Labeled breaks are not supported");

        return stmt("break;");
    }

    private String transpileSwitch(SwitchStmt stmt) {
        var result = new StringBuilder(stmt("switch (%s) {\n", transpileExpression(stmt.getSelector())));
        currentIndent++;

        stmt.getEntries().forEach(entry -> {
            if (entry.getLabels().isEmpty()){
                result.append(stmt("default:\n"));
            } else {
                entry.getLabels().forEach(label -> result.append(stmt("case %s:\n", transpileExpression(label))));
            }

            currentIndent++;
            result.append(combine(entry.getStatements(), this::transpileStatement)
                    .<String>transform(s -> s.isEmpty() ? "" : s + "\n"));
            currentIndent--;
        });

        currentIndent--;
        return result + stmt("}");
    }

    private String transpileForEach(ForEachStmt stmt) {
        var result = stmt("for (%s : %s) {\n",
                transpileExpression(stmt.getVariable()), transpileExpression(stmt.getIterable())
        );
        currentIndent++;
        result += transpileStatement(stmt.getBody()) + "\n";

        currentIndent--;
        return result + stmt("}");
    }

    private String transpileFor(ForStmt stmt) {
        var result = stmt("for (%s; %s; %s) {\n",
                stmt.getInitialization().stream().map(this::transpileExpression).collect(Collectors.joining(", ")),
                stmt.getCompare().map(this::transpileExpression).orElse(""),
                stmt.getUpdate().stream().map(this::transpileExpression
            ).collect(Collectors.joining(", ")));
        currentIndent++;
        result += transpileStatement(stmt.getBody()) + "\n";

        currentIndent--;
        return result + stmt("}");
    }

    private String transpileDoWhile(DoStmt stmt) {
        var result = stmt("do {\n");
        currentIndent++;
        result += transpileStatement(stmt.getBody()) + "\n";

        currentIndent--;
        return result + stmt("} while (%s);", transpileExpression(stmt.getCondition()));
    }

    private String transpileWhile(WhileStmt stmt) {
        var result = stmt("while (%s) {\n", transpileExpression(stmt.getCondition()));
        currentIndent++;
        result += transpileStatement(stmt.getBody()) + "\n";

        currentIndent--;
        return result + stmt("}");
    }

    private String transpileIf(IfStmt ifStmt) {
        var result = stmt("if (%s) {\n", transpileExpression(ifStmt.getCondition()));

        currentIndent++;
        result += transpileStatement(ifStmt.getThenStmt()) + "\n";

        if (ifStmt.getElseStmt().isPresent()) {
            currentIndent--;
            result += stmt("} else {\n");

            currentIndent++;
            result += transpileStatement(ifStmt.getElseStmt().get()) + "\n";
        }

        currentIndent--;
        return result + stmt("}");
    }

    private String transpileExpression(Expression stmt) {
        return switch (stmt) {
            case VariableDeclarationExpr expr -> transpileVariableDeclaration(expr);
            case BinaryExpr expr -> transpileBinary(expr);
            case UnaryExpr expr -> transpileUnary(expr);
            case LiteralExpr expr -> transpileLiteral(expr);
            case AssignExpr expr -> transpileAssign(expr);
            case CastExpr expr -> transpileCast(expr);
            case ConditionalExpr expr -> transpileConditional(expr);
            case EnclosedExpr expr -> "(" + transpileExpression(expr.getInner()) + ")";
            case NameExpr expr -> expr.getNameAsString();
            default -> throw new IllegalStateException("Unexpected value: " + stmt);
        };
    }

    private String transpileConditional(ConditionalExpr expr) {
        return "%s ? %s : %s".formatted(
                transpileExpression(expr.getCondition()),
                transpileExpression(expr.getThenExpr()),
                transpileExpression(expr.getElseExpr()));
    }

    private String transpileCast(CastExpr expr) {
        return "(%s)%s".formatted(transpileType(expr.getType()), transpileExpression(expr.getExpression()));
    }

    private String transpileAssign(AssignExpr expr) {
        if (expr.getOperator() == AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT) {
            throw new IllegalArgumentException("Unsupported >>>=");
        }

        return transpileExpression(expr.getTarget()) + " " + expr.getOperator().asString() + " " + transpileExpression(expr.getValue());
    }

    private String transpileUnary(UnaryExpr expr) {
        return expr.getOperator().isPostfix() ?
                transpileExpression(expr.getExpression()) + expr.getOperator().asString()
                : expr.getOperator().asString() + transpileExpression(expr.getExpression());
    }

    private String transpileBinary(BinaryExpr expr) {
        var left = transpileExpression(expr.getLeft());
        var right = transpileExpression(expr.getRight());

        var operator = expr.getOperator().asString();
        if (operator.equals(">>>")) {
            throw new IllegalArgumentException("Unsupported >>>");
        }

        return left + " " + operator + " " + right;
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

    private String transpileVariableDeclaration(VariableDeclarationExpr expr) {
        var builder = new StringBuilder();
        expr.getModifiers().stream().map(this::transpileModifier).forEach(m -> builder.append(m).append(" "));
        builder.append(transpileType(expr.getCommonType())).append(" ");

        expr.getVariables().stream().map(v ->
                v.getInitializer().map(i -> v.getNameAsString() + " = " + transpileExpression(i)).orElse(v.getNameAsString())
        ).collect(Collectors.collectingAndThen(Collectors.joining(", "), builder::append));

        return builder.toString();
    }

    private String transpileModifier(Modifier modifier) {
        return switch (modifier.getKeyword()) {
            case FINAL -> "const";
            default -> throw new IllegalStateException("Unexpected value: " + modifier.getKeyword());
        };
    }

    private String transpileType(Type type) {
        var res = type.asString();

        if (type.isVarType() || res.equals("var")) {
            res = "auto";
        } else if (res.equals("boolean")) {
            res = "bool";
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

    private String stmt(String s, Object... args) {
        var r = s.formatted(args).indent(currentIndent * INDENT_LENGTH);
        if (!s.endsWith("\n"))
            r = r.substring(0, r.length() - 1);
        return r;
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