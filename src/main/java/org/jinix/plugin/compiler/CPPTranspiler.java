package org.jinix.plugin.compiler;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import org.jetbrains.annotations.Nullable;
import org.jinix.plugin.MethodSourceReport;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public class CPPTranspiler extends Transpiler {
    private static final int INDENT_LENGTH = 4;

    //TODO
    private final Set<Include> toInclude = new HashSet<>();

    // Per transpilation:
    private int currentIndent;
    private boolean requireThisClassObject;
    private ResolvedType thisType;

    protected CPPTranspiler(TypeSolver solver, MethodSourceReport report) {
        super(solver, report);
    }

    @Override
    protected String transpileBody(MethodDeclaration method) {
        this.currentIndent = 0;
        this.requireThisClassObject = false;
        this.thisType = new ReferenceTypeImpl(solver.solveType(sourceReport.getMethodDeclaringClass(method)));

        var result = new StringBuilder();
        var body = transpileStatementOrBlock(method.getBody().orElseThrow());

        if (requireThisClassObject) {
            result.append("jclass ").append(THIS_CLASS).append(" = ").append(jniGetObjectClass(THIS_PARAM)).append(";\n");
        }
        result.append(statementBlockToCode(body));

        return result.toString();
    }

    private List<CPPStatement> transpileStatementOrBlock(Statement statement) {
        return statement instanceof BlockStmt stmt ? stmt.getStatements().stream().map(this::transpileStatement).toList()
                : List.of(transpileStatement(statement));
    }

    private CPPStatement transpileStatement(Statement statement) {
        return switch (statement) {
            case ExpressionStmt stmt -> transpileExpression(stmt.getExpression());
            case IfStmt stmt -> transpileIf(stmt);
            case SwitchStmt stmt -> transpileSwitch(stmt);
            case WhileStmt stmt -> transpileWhile(stmt);
            case DoStmt stmt -> transpileDoWhile(stmt);
            case ForStmt stmt -> transpileFor(stmt);
            case ForEachStmt stmt -> transpileForEach(stmt);
//            case BlockStmt stmt -> transpileBlock(stmt);
            case BreakStmt stmt -> transpileBreak(stmt);
            case ContinueStmt stmt -> transpileContinue(stmt);
            case ReturnStmt stmt -> transpileReturn(stmt);
            case ThrowStmt stmt -> transpileThrow(stmt);
            case EmptyStmt s -> new CPPStatement(stmt(s.toString()));
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    private CPPStatement transpileThrow(ThrowStmt stmt) {
        return new CPPStatement(stmt("throw %s;", transpileExpression(stmt.getExpression())));
    }

    private CPPStatement transpileReturn(ReturnStmt stmt) {
        return new CPPStatement(stmt(stmt.getExpression().map(s -> stmt("return %s;", transpileExpression(s))).orElse("return;")));
    }

    private CPPStatement transpileContinue(ContinueStmt stmt) {
        if (stmt.getLabel().isPresent())
            throw new IllegalArgumentException("Labeled continue statements are not supported");

        return new CPPStatement(stmt("continue;"));
    }

    private CPPStatement transpileBreak(BreakStmt stmt) {
        if (stmt.getLabel().isPresent())
            throw new IllegalArgumentException("Labeled breaks are not supported");

        return new CPPStatement(stmt("break;"));
    }

    private CPPStatement transpileSwitch(SwitchStmt stmt) {
        var result = new StringBuilder(stmt("switch (%s) {\n", transpileExpression(stmt.getSelector())));
        List<List<CPPStatement>> blocks = new ArrayList<>();
        currentIndent++;

        stmt.getEntries().forEach(entry -> {
            if (entry.getLabels().isEmpty()){
                result.append(stmt("default:\n"));
            } else {
                entry.getLabels().forEach(label -> result.append(stmt("case %s:\n", transpileExpression(label))));
            }

            currentIndent++;
            var block = entry.getStatements().stream().flatMap(s -> transpileStatementOrBlock(s).stream()).toList();
            if (!block.isEmpty()) {
                blocks.add(block);
                result.append("#\n");
            }

            currentIndent--;
        });

        currentIndent--;
        result.append(stmt("}"));
        return new CPPStatement(BlockType.SWITCH, blocks, formatingBlocks(result.toString()));
    }

    private CPPStatement transpileForEach(ForEachStmt stmt) {
        var result = stmt("for (%s : %s) {\n#\n}",
                transpileExpression(stmt.getVariable()), transpileExpression(stmt.getIterable())
        );
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileFor(ForStmt stmt) {
        var result = stmt("for (%s; %s; %s) {\n#\n}",
                stmt.getInitialization().stream().map(s -> transpileExpression(s).toString()).collect(Collectors.joining(", ")),
                stmt.getCompare().map(s -> transpileExpression(s).toString()).orElse(""),
                stmt.getUpdate().stream().map(s -> transpileExpression(s).toString()).collect(Collectors.joining(", "))
        );
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileDoWhile(DoStmt stmt) {
        var result = stmt("do {\n#\n");
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;
        result += stmt("} while (%s);", transpileExpression(stmt.getCondition()));

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileWhile(WhileStmt stmt) {
        var code = stmt("while (%s) {\n#\n}", transpileExpression(stmt.getCondition()));
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(code));
    }

    private CPPStatement transpileIf(IfStmt ifStmt) {
        var code = stmt("if (%s) {\n#\n", transpileExpression(ifStmt.getCondition()));
        List<List<CPPStatement>> blocks = new ArrayList<>();

        currentIndent++;
        blocks.add(transpileStatementOrBlock(ifStmt.getThenStmt()));

        if (ifStmt.getElseStmt().isPresent()) {
            currentIndent--;
            code += stmt("} else {\n#\n");

            currentIndent++;
            blocks.add(transpileStatementOrBlock(ifStmt.getElseStmt().get()));
        }

        currentIndent--;
        code += stmt("}");
        return new CPPStatement(BlockType.IF, blocks, formatingBlocks(code));
    }

    private CPPExpression transpileExpression(Expression stmt) {
        return switch (stmt) {
//            case MethodCallExpr expr -> transpileCall(expr);
            case VariableDeclarationExpr expr -> transpileVariableDeclaration(expr);
            case BinaryExpr expr -> transpileBinary(expr);
            case UnaryExpr expr -> transpileUnary(expr);
            case LiteralExpr expr -> transpileLiteral(expr);
            case AssignExpr expr -> transpileAssign(expr);
            case CastExpr expr -> transpileCast(expr);
            case ConditionalExpr expr -> transpileConditional(expr);
            case EnclosedExpr expr -> this.transpileExpression(expr.getInner()).withCode(e -> "(" + e.code + ")");
            case NameExpr expr -> new CPPExpression(expr.getNameAsString(), expr.calculateResolvedType());
            case ThisExpr expr -> new CPPExpression(THIS_PARAM, thisType);  // Only as an argument, not field/method access
            default -> throw new IllegalStateException("Unexpected value: " + stmt);
        };
    }

    private String transpileCall(MethodCallExpr expr) {
        var scopeExpr = expr.getScope().orElse(null);
        String scope;
        String scopeClass;
        if (scopeExpr == null || scopeExpr instanceof ThisExpr) {
            this.requireThisClassObject = true;
            scope = THIS_PARAM;
            scopeClass = THIS_CLASS;
        } else {
            throw new RuntimeException("TODO");
        }

        var signatures = "\"()V\"";
        //TODO return type

        return jniEnvCall("CallVoidMethod", scope,
                jniEnvCall("GetMethodID", scopeClass, "\"" + expr.getName() + "\"", signatures));
    }

    private CPPExpression transpileConditional(ConditionalExpr expr) {
        return new CPPExpression("%s ? %s : %s", expr.calculateResolvedType(),
                transpileExpression(expr.getCondition()),
                transpileExpression(expr.getThenExpr()),
                transpileExpression(expr.getElseExpr()));
    }

    private CPPExpression transpileCast(CastExpr expr) {
        return new CPPExpression("(%s)%s", expr.calculateResolvedType(),
                transpileType(expr.getType()), transpileExpression(expr.getExpression()));
    }

    private CPPExpression transpileAssign(AssignExpr expr) {
        if (expr.getOperator() == AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT) {
            throw new IllegalArgumentException("Unsupported >>>=");
        }

        return new CPPExpression("%s %s %s", expr.calculateResolvedType(),
                transpileExpression(expr.getTarget()), expr.getOperator().asString(), transpileExpression(expr.getValue()));
    }

    private CPPExpression transpileUnary(UnaryExpr expr) {
        return expr.getOperator().isPostfix() ?
                new CPPExpression("%s%s", expr.calculateResolvedType(),
                        transpileExpression(expr.getExpression()), expr.getOperator().asString())
                : new CPPExpression("%s%s", expr.calculateResolvedType(),
                expr.getOperator().asString(), transpileExpression(expr.getExpression()));
    }

    private CPPExpression transpileBinary(BinaryExpr expr) {
        var left = transpileExpression(expr.getLeft());
        var right = transpileExpression(expr.getRight());

        var operator = expr.getOperator().asString();
        if (operator.equals(">>>")) {
            throw new IllegalArgumentException("Unsupported >>>");
        }

        return new CPPExpression("%s %s %s", expr.calculateResolvedType(),  left, operator, right);
    }

    private CPPExpression transpileLiteral(LiteralExpr expr) {
        return switch (expr) {
            case TextBlockLiteralExpr l -> new CPPExpression("\"" + l.getValue().stripIndent().replace("\n", "\\\n") + "\"", l.calculateResolvedType());
            case NullLiteralExpr l -> new CPPExpression("nullptr", l.calculateResolvedType());
            case BooleanLiteralExpr l -> new CPPExpression(l.toString(), l.calculateResolvedType());
            case LiteralStringValueExpr l -> new CPPExpression(l.toString(), l.calculateResolvedType());
            default -> throw new IllegalStateException("Unexpected value: " + expr);
        };
    }

    private CPPExpression transpileVariableDeclaration(VariableDeclarationExpr expr) {
        var builder = new StringBuilder();
        expr.getModifiers().stream().map(this::transpileModifier).forEach(m -> builder.append(m).append(" "));
        builder.append(transpileType(expr.getCommonType())).append(" ");

        expr.getVariables().stream().map(v ->
                v.getInitializer().map(i -> v.getNameAsString() + " = " + transpileExpression(i)).orElse(v.getNameAsString())
        ).collect(Collectors.collectingAndThen(Collectors.joining(", "), builder::append));

        return new CPPExpression(builder.toString(), null); //FIXME Explosive
    }

    private String transpileModifier(Modifier modifier) {
        return switch (modifier.getKeyword()) {
            case FINAL -> "const";
            default -> throw new IllegalStateException("Unexpected value: " + modifier.getKeyword());
        };
    }

    private CPPExpression transpileType(Type type) {
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

        return new CPPExpression(res, type.resolve());
    }

    private String jniGetObjectClass(String obj) {
        return jniEnvCall("GetObjectClass", obj);
    }

    private String jniEnvCall(String functionName, String... params) {
        return "(*%s)->%s(%s%s)".formatted(ENV_PARAM, functionName,
                ENV_PARAM, Arrays.stream(params).map(s -> ", " + s).collect(Collectors.joining()));
    }

    @Override
    protected String getFileExtension() {
        return "cpp";
    }

    private <T extends Node> String combine(List<T> nodes, Function<T, String> mapper){
        return nodes.stream().map(mapper).collect(Collectors.joining("\n"));
    }

    private String stmt(String s, Object... args) {
        return stmtWithIndent(s, currentIndent, args);
    }

    private String stmtWithIndent(String s, int indent, Object... args) {
        var r = s.formatted(args).indent(indent * INDENT_LENGTH);
        if (!s.endsWith("\n"))
            r = r.substring(0, r.length() - 1);
        return r;
    }

    private Function<List<List<CPPStatement>>, String> formatingBlocks(String code) {
        return blocks -> {
            var parts = code.split("#");
            var builder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                builder.append(parts[i].replaceAll(" +$", "")).append(statementBlockToCode(blocks.get(i)));
            }

            builder.append(parts[parts.length - 1]);

            return builder.toString();
        };
    }

    private String statementBlockToCode(List<CPPStatement> cppStatements) {
        return cppStatements.stream().map(CPPStatement::getCodeAsStatement).collect(Collectors.joining("\n"));
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

    private static class CPPStatement {
        private final Function<List<List<CPPStatement>>, String> codeProvider;
        public final @Nullable BlockType blockType;
        public final List<List<CPPStatement>> blocks = new ArrayList<>();

        public CPPStatement(String code) {
            this.codeProvider = bs -> code;
            this.blockType = null;
        }

        @SuppressWarnings("NullableProblems")
        public CPPStatement(BlockType blockType, List<List<CPPStatement>> blocks, Function<List<List<CPPStatement>>, String> codeProvider) {
            this.codeProvider = codeProvider;
            this.blockType = blockType;
            this.blocks.addAll(blocks);
        }

        public String getCodeAsStatement() {
            return codeProvider.apply(blocks);
        }

        @Override
        public String toString() {
            return getCodeAsStatement();
        }
    }

    private enum BlockType {
        IF, FOR, WHILE, SWITCH
    }

    private class CPPExpression extends CPPStatement {
        public final String code;
        public final ResolvedType type;
        public final int indent;   // Captures indent at expression creation

        public CPPExpression(String code, ResolvedType type, Object... nodes) {
            this(code.formatted(nodes), type);
        }

        public CPPExpression(String code, ResolvedType type) {
            super(code);
            this.code = code;
            this.type = type;
            this.indent = currentIndent;
        }

        @Override
        public String getCodeAsStatement() {
            return stmtWithIndent(code + ";", indent);
        }

        @Override
        public String toString() {
            return code;
        }

        public CPPExpression withCode(Function<CPPExpression, String> codeProvider) {
            return new CPPExpression(codeProvider.apply(this), this.type);
        }
    }
}