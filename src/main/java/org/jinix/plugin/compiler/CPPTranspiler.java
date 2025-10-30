package org.jinix.plugin.compiler;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import org.jetbrains.annotations.Nullable;
import org.jinix.plugin.MethodSourceReport;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CPPTranspiler extends Transpiler {
    private static final int INDENT_LENGTH = 4;

    //TODO
    private final Set<Include> toInclude = new HashSet<>();

    // Per transpilation:
    private int currentIndent;
    private ResolvedType thisType;

    protected CPPTranspiler(TypeSolver solver, MethodSourceReport report) {
        super(solver, report);
    }

    @Override
    protected String transpileBody(MethodDeclaration method) {
        this.currentIndent = 0;
        this.thisType = new ReferenceTypeImpl(solver.solveType(sourceReport.getMethodDeclaringClass(method)));

        var result = new StringBuilder();
        var body = transpileStatementOrBlock(method.getBody().orElseThrow());
        new JniStatementsFinalizer().finalize(body);

        result.append(statementBlockToCode(body));

        return result.toString();
    }

    private List<CPPStatement> transpileStatementOrBlock(Statement statement) {
        return statement instanceof BlockStmt stmt ? stmt.getStatements().stream().map(this::transpileStatement).collect(Collectors.toList())
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
        var expr = transpileExpression(stmt.getExpression());
        return new CPPStatement(stmt("throw %s;", expr)).inherit(expr);
    }

    private CPPStatement transpileReturn(ReturnStmt stmt) {
        if (stmt.getExpression().isPresent()){
            var expr = transpileExpression(stmt.getExpression().get());
            return new CPPStatement(stmt("return %s;", expr)).inherit(expr);
        }

        return new CPPStatement("return;");
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
        var input = transpileExpression(stmt.getSelector());
        var result = new StringBuilder(stmt("switch (%s) {\n", input));
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
        return new CPPStatement(BlockType.SWITCH, blocks, formatingBlocks(result.toString())).inherit(input);
    }

    private CPPStatement transpileForEach(ForEachStmt stmt) {
        var collection = transpileExpression(stmt.getIterable());
        var result = stmt("for (%s : %s) {\n#\n}", transpileExpression(stmt.getVariable()), collection);
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result)).inherit(collection);
    }

    private CPPStatement transpileFor(ForStmt stmt) {
        var statements = new StatementsList();
        var result = stmt("for (%s; %s; %s) {\n#\n}",
                stmt.getInitialization().stream().map(s -> statements.write(transpileExpression(s)).toString()).collect(Collectors.joining(", ")),
                stmt.getCompare().map(s -> statements.write(transpileExpression(s)).toString()).orElse(""),
                stmt.getUpdate().stream().map(s -> statements.write(transpileExpression(s)).toString()).collect(Collectors.joining(", "))
        );
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result)).inherit(statements);
    }

    private CPPStatement transpileDoWhile(DoStmt stmt) {
        var result = stmt("do {\n#\n");
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;
        var condition = transpileExpression(stmt.getCondition());
        result += stmt("} while (%s);", condition);

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(result)).inherit(condition);
    }

    private CPPStatement transpileWhile(WhileStmt stmt) {
        var condition = transpileExpression(stmt.getCondition());
        var code = stmt("while (%s) {\n#\n}", condition);
        currentIndent++;
        var block = transpileStatementOrBlock(stmt.getBody());
        currentIndent--;

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(code)).inherit(condition);
    }

    private CPPStatement transpileIf(IfStmt ifStmt) {
        var condition = transpileExpression(ifStmt.getCondition());
        var code = stmt("if (%s) {\n#\n", condition);
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
        return new CPPStatement(BlockType.IF, blocks, formatingBlocks(code)).inherit(condition);
    }

    private CPPExpression transpileExpression(Expression stmt) {
        return switch (stmt) {
            case MethodCallExpr expr -> transpileCall(expr);
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

    private CPPExpression transpileCall(MethodCallExpr expr) {
        var scopeExpr = expr.getScope().orElse(null);
        String scope, scopeClass;
        var statements = new StatementsList();

        if (scopeExpr == null || scopeExpr instanceof ThisExpr) {
            scope = THIS_PARAM;
            scopeClass = thisType.describe();
        } else {
            var transpiled = transpileExpression(scopeExpr);
            statements.add(transpiled);
            scope = transpiled.code;
            scopeClass = transpiled.type.describe();
        }

        var resolvedMethod = expr.resolve();
        var findClass = jniFindClass(scopeClass);
        var getMethodId = jniGetMethodId(resolvedMethod, findClass);

        List<String> params = new ArrayList<>();
        for (Expression argument : expr.getArguments()) {
            params.add(statements.write(transpileExpression(argument)).toString());
        }

        CPPExpression call;
        if (resolvedMethod.getReturnType().isVoid()) {
            params.addFirst(scope);
            params.addFirst(getMethodId.resultingVar);
            call = new CPPExpression(jniEnvCall("CallVoidMethod", params.toArray(String[]::new)), resolvedMethod.getReturnType());
        } else {
            throw new RuntimeException("TODO");
        }

        call.jniStatements.addAll(List.of(findClass, getMethodId));
        call.inherit(statements);

        return call;
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

        return new CPPExpression("%s %s %s", expr.calculateResolvedType(), left, operator, right);
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

        var statements = new StatementsList();
        expr.getVariables().stream().map(v ->
                v.getInitializer().map(i -> v.getNameAsString() + " = " + statements.write(transpileExpression(i))).orElse(v.getNameAsString())
        ).collect(Collectors.collectingAndThen(Collectors.joining(", "), builder::append));

        //FIXME null here is explosive
        return (CPPExpression) new CPPExpression(builder.toString(), null).inherit(statements);
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

    // ---------- JNI TOOLS ----------
    private static String jniEnvCall(String functionName, String... params) {
        return "(*%s)->%s(%s)".formatted(ENV_PARAM, functionName, String.join(", ", params));
    }

    private static String typeToJniSignature(String name) {
        return switch (name) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            case "void" -> "V";
            default -> {
                if (name.startsWith("[")) {
                    yield name.replace(".", "/");
                } else {
                    yield "L" + name.replace(".", "/") + ";";
                }
            }
        };
    }

    private static String getMethodSignature(ResolvedMethodDeclaration method) {
        return "(%s)%s".formatted(
                //TODO support for generics
                IntStream.range(0, method.getNumberOfParams()).mapToObj(method::getParam)
                        .map(ResolvedParameterDeclaration::describeType)
                        .map(CPPTranspiler::typeToJniSignature).collect(Collectors.joining()),
                typeToJniSignature(method.getReturnType().describe())
        );
    }

    private static String uniqueMethodIdName(ResolvedMethodDeclaration method) {
        var signature = getMethodSignature(method).replaceAll("[();/]", "").replace("[", "A");
        var className = method.getClassName().replace(".", "");

        return className + "_" + method.getName() + "_" + signature;
    }

    private JniStatement jniGetMethodId(ResolvedMethodDeclaration method, JniStatement getClassStatement) {
        var varName = uniqueMethodIdName(method);
        return new JniStatement("jmethodID " + varName + " = " +
                jniEnvCall("GetMethodID", getClassStatement.resultingVar, "\"" + getMethodSignature(method) + "\""),
                JniStatementType.GET_METHOD_ID, varName
        );
    }

    private JniStatement jniFindClass(String name) {
        var varName = "class_" + name.replace(".", "");
        return new JniStatement("jclass " + varName + " = " + jniEnvCall("FindClass", "\"" + name.replace(".", "/") + "\""),
                JniStatementType.FIND_CLASS, varName
        );
    }

    // ---------- UTILS ----------

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

    public static class CPPStatement {
        private final Function<List<List<CPPStatement>>, String> codeProvider;
        public final @Nullable BlockType blockType;
        public final List<List<CPPStatement>> blocks = new ArrayList<>();
        public final LinkedHashSet<JniStatement> jniStatements = new LinkedHashSet<>();

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

        //TODO write test to make sure statements inherit jni properly
        public CPPStatement inherit(CPPStatement... statements) {
            for (CPPStatement statement : statements) {
                jniStatements.addAll(statement.jniStatements);
            }

            return this;
        }

        public CPPStatement inherit(Collection<CPPStatement> statements) {
            for (CPPStatement statement : statements) {
                jniStatements.addAll(statement.jniStatements);
            }

            return this;
        }

        public String getCodeAsStatement() {
            return codeProvider.apply(blocks);
        }

        @Override
        public String toString() {
            return getCodeAsStatement();
        }
    }

    public enum BlockType {
        IF, FOR, WHILE, SWITCH
    }

    public class JniStatement extends CPPStatement {
        public final String code;
        public final JniStatementType type;
        public final String resultingVar;
        public final int indent;   // Captures indent at expression creation

        public JniStatement(String code, JniStatementType type, String resultingVar) {
            super(code);
            this.code = code;
            this.type = type;
            this.resultingVar = resultingVar;
            this.indent = currentIndent;
        }

        @Override
        public String getCodeAsStatement() {
            return stmtWithIndent(code, indent) + ";";
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            JniStatement that = (JniStatement) o;
            return code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    public enum JniStatementType {
        GET_METHOD_ID("GetMethodID"),
        FIND_CLASS("FindClass"),
        GET_OBJECT_CLASS("GetObjectClass");

        public final String name;

        JniStatementType(String name) {
            this.name = name;
        }
    }

    public class CPPExpression extends CPPStatement {
        public final String code;
        public final ResolvedType type;
        public final int indent;   // Captures indent at expression creation

        public CPPExpression(String code, ResolvedType type, Object... nodes) {
            this(code.formatted(nodes), type);
            for (Object node : nodes) {
                if (node instanceof CPPStatement s) inherit(s);
            }
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

    private static class StatementsList extends ArrayList<CPPStatement> {
        public <T extends CPPExpression> T write(T cppStatement) {
            this.add(cppStatement);
            return cppStatement;
        }
    }
}