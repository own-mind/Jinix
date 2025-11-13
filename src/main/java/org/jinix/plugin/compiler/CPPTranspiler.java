package org.jinix.plugin.compiler;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import org.jetbrains.annotations.Nullable;
import org.jinix.Jinix;
import org.jinix.plugin.MethodSourceReport;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CPPTranspiler extends Transpiler {
    private static final int INDENT_LENGTH = 4;
    protected static final String ENV_PARAM = "env";
    protected static final String THIS_PARAM = "thisObject";

    private final Set<Include> toInclude = new HashSet<>();
    public final LinkedHashSet<JniStatement> jniStatements = new LinkedHashSet<>();
    // Contains used functions, which is used for filtering unused util functions
    private final Set<String> usedUtilFunctions = new HashSet<>();

    // Per transpilation:
    private CodeTreeLookup lookup;
    private ResolvedType thisType;

    protected CPPTranspiler(TypeSolver solver, MethodSourceReport report) {
        super(solver, report);
    }

    @Override
    protected void beforeMethods(PrintWriter out) throws Exception {
        out.println("#include \"jinix.h\"");
        toInclude.forEach(i -> out.println("#include \"" + i.getFile() + "\""));
        out.println();

        try (var stream = CPPTranspiler.class.getResourceAsStream("/jinix_utils.cpp")) {
            assert stream != null;
            var utilContents = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            out.println(removeUnusedUtils(utilContents));
        }
        out.println();

        if (!jniStatements.isEmpty()) {
            sortJni();
            out.println("// --- GLOBAL JNI OBJECTS ---");
            jniStatements.forEach(s -> out.println(s.declaration));
            out.println();
            out.println("void Java_" + Jinix.class.getName().replace('.', '_') + "_init(JNIEnv *env) {");
            jniStatements.forEach(s -> out.println(indent(s.initialization)));
            out.println("}");
        }
    }

    @Override
    protected void afterMethods(PrintWriter writer) {

    }

    @Override
    protected String transpileMethod(HeaderGenerator.JniFunctionDeclaration declaration, String className, MethodDeclaration method) {
        return "%s %s(JNIEnv *%s, jobject %s%s) {\n%s}".formatted(
                declaration.returnType(),
                declaration.name(),
                ENV_PARAM, THIS_PARAM,
                declaration.parameters().stream()
                        .map(p -> ", " + jniType(p.getType()) + " " + p.getName())
                        .collect(Collectors.joining()),
                transpileBody(className, method).indent(4)
        );
    }

    public String transpileBody(String declaringClass, MethodDeclaration method) {
        this.thisType = new ReferenceTypeImpl(solver.solveType(declaringClass));
        this.lookup = new CodeTreeLookup(method);

        var result = new StringBuilder();
        var body = transpileStatementOrBlock(method.getBody().orElseThrow());
        result.append(statementBlockToCode(body));

        return result.toString();
    }

    private List<CPPStatement> transpileStatementOrBlock(Statement statement) {
        return statement instanceof BlockStmt stmt ? stmt.getStatements().stream().map(this::transpileStatement).collect(Collectors.toList())
                : new ArrayList<>(List.of(transpileStatement(statement)));
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
        return new CPPStatement(stmt("throw %s;", expr));
    }

    private CPPStatement transpileReturn(ReturnStmt stmt) {
        if (stmt.getExpression().isPresent()){
            var expr = transpileExpression(stmt.getExpression().get());
            return new CPPStatement(stmt("return %s;", expr));
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

        stmt.getEntries().forEach(entry -> {
            if (entry.getLabels().isEmpty()){
                result.append("default:\n");
            } else {    //TODO it is important to see how this will work out for Static.fields and enums (and if does, add jni inheritance)
                entry.getLabels().forEach(label -> result.append(stmt("case %s:\n", transpileExpression(label))));
            }

            var block = entry.getStatements().stream().flatMap(s -> transpileStatementOrBlock(s).stream()).collect(Collectors.toList());
            if (!block.isEmpty()) {
                blocks.add(block);
                result.append("#\n");
            }
        });

        result.append(stmt("}"));
        return new CPPStatement(BlockType.SWITCH, blocks, formatingBlocks(result.toString()));
    }

    private CPPStatement transpileForEach(ForEachStmt stmt) {
        var collection = transpileExpression(stmt.getIterable());
        var result = stmt("for (%s : %s) {\n#\n}", transpileExpression(stmt.getVariable()), collection);
        var block = transpileStatementOrBlock(stmt.getBody());

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileFor(ForStmt stmt) {
        var result = stmt("for (%s; %s; %s) {\n#\n}",
                stmt.getInitialization().stream().map(s -> transpileExpression(s).toString()).collect(Collectors.joining(", ")),
                stmt.getCompare().map(s -> transpileExpression(s).toString()).orElse(""),
                stmt.getUpdate().stream().map(s -> transpileExpression(s).toString()).collect(Collectors.joining(", "))
        );
        var block = transpileStatementOrBlock(stmt.getBody());

        return new CPPStatement(BlockType.FOR, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileDoWhile(DoStmt stmt) {
        var result = stmt("do {\n#\n");
        var block = transpileStatementOrBlock(stmt.getBody());
        var condition = transpileExpression(stmt.getCondition());
        result += stmt("} while (%s);", condition);

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(result));
    }

    private CPPStatement transpileWhile(WhileStmt stmt) {
        var condition = transpileExpression(stmt.getCondition());
        var code = stmt("while (%s) {\n#\n}", condition);
        var block = transpileStatementOrBlock(stmt.getBody());

        return new CPPStatement(BlockType.WHILE, List.of(block), formatingBlocks(code));
    }

    private CPPStatement transpileIf(IfStmt ifStmt) {
        var condition = transpileExpression(ifStmt.getCondition());
        var code = stmt("if (%s) {\n#\n", condition);
        List<List<CPPStatement>> blocks = new ArrayList<>();

        blocks.add(transpileStatementOrBlock(ifStmt.getThenStmt()));

        if (ifStmt.getElseStmt().isPresent()) {
            code += stmt("} else {\n#\n");
            blocks.add(transpileStatementOrBlock(ifStmt.getElseStmt().get()));
        }

        code += stmt("}");
        return new CPPStatement(BlockType.IF, blocks, formatingBlocks(code));
    }

    private CPPExpression transpileExpression(Expression stmt) {
        return switch (stmt) {
            case FieldAccessExpr expr -> transpileFieldAccess(expr);   // For getting the value only, see transpileAssign for setting
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

    private CPPExpression transpileFieldAccess(FieldAccessExpr expr) {
        //TODO special case for `length` in arrays
        String scope, scopeClass;
        //TODO Handle fields without scope `this`
        if (expr.getScope() instanceof ThisExpr) {
            scope = THIS_PARAM;
            scopeClass = thisType.describe();
        } else {
            var transpiled = transpileExpression(expr.getScope());
            scope = transpiled.code;
            scopeClass = transpiled.type.describe();
        }

        var resolvedField = expr.resolve().asField();
        var findClass = jniFindClass(scopeClass);
        var getFieldId = resolvedField.isStatic() ? jniGetStaticFieldId(resolvedField, findClass) :
                jniGetFieldId(resolvedField, findClass);

        String callType, cast = "", scopeVar, type;
        if (resolvedField.isStatic()) {
            scopeVar = findClass.resultingVar;
            callType = "GetStatic";
        } else {
            scopeVar = scope;
            callType = "Get";
        }

        if (resolvedField.getType().isVoid()) {
            type = "Void";
        } else if (resolvedField.getType().isPrimitive()) {
            var retType = resolvedField.getType().describe();
            cast = "(" + retType + ")";
            type = Character.toUpperCase(retType.charAt(0)) + retType.substring(1);
        } else {
            type = "Object";
        }

        registerJniStatement(findClass, getFieldId);
        return new CPPExpression(cast + jniEnvCall(callType + type + "Field", scopeVar, getFieldId.resultingVar), resolvedField.getType());
    }

    private CPPExpression transpileCall(MethodCallExpr expr) {
        var scopeExpr = expr.getScope().orElse(null);
        String scope, scopeClass;
        if (scopeExpr == null || scopeExpr instanceof ThisExpr) {
            scope = THIS_PARAM;
            scopeClass = thisType.describe();
        } else {
            var transpiled = transpileExpression(scopeExpr);
            scope = transpiled.code;
            scopeClass = transpiled.type.describe();
        }

        var resolvedMethod = expr.resolve();
        var findClass = jniFindClass(scopeClass);
        var getMethodId = resolvedMethod.isStatic() ? jniGetStaticMethodId(resolvedMethod, findClass) :
                jniGetMethodId(resolvedMethod, findClass);

        // Params order should be: jobject/jclass, methodId, ...provided args. The first one is added in the block below
        List<String> args = new ArrayList<>();
        args.add(getMethodId.resultingVar);
        for (Expression argument : expr.getArguments()) {
            args.add(transpileExpression(argument).toString());
        }

        String callType, cast = "", type;
        if (resolvedMethod.isStatic()) {
            args.addFirst(findClass.resultingVar);
            callType = "CallStatic";
        } else {
            args.addFirst(scope);
            callType = "Call";
        }

        if (resolvedMethod.getReturnType().isVoid()) {
            type = "Void";
        } else if (resolvedMethod.getReturnType().isPrimitive()) {
            var retType = resolvedMethod.getReturnType().describe();
            cast = "(" + retType + ")";    // Here we cast to C type from j-type
            type = Character.toUpperCase(retType.charAt(0)) + retType.substring(1);
        } else {
            type = "Object";
        }

        registerJniStatement(findClass, getMethodId);
        return new CPPExpression(cast + jniEnvCall(callType + type + "Method", args.toArray(String[]::new)), resolvedMethod.getReturnType());
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

        if (!expr.getTarget().isFieldAccessExpr()) {
            return new CPPExpression("%s %s %s", expr.calculateResolvedType(),
                    transpileExpression(expr.getTarget()), expr.getOperator().asString(), transpileExpression(expr.getValue()));
        }

        var fieldExpr = expr.getTarget().asFieldAccessExpr();
        String scope, scopeClass;
        //TODO Handle fields without scope (`this` and static)
        if (fieldExpr.getScope() instanceof ThisExpr) {
            scope = THIS_PARAM;
            scopeClass = thisType.describe();
        } else {
            var transpiled = transpileExpression(fieldExpr.getScope());
            scope = transpiled.code;
            scopeClass = transpiled.type.describe();
        }

        var resolvedField = fieldExpr.resolve().asField();
        var findClass = jniFindClass(scopeClass);
        var getFieldId = resolvedField.isStatic() ? jniGetStaticFieldId(resolvedField, findClass) :
                jniGetFieldId(resolvedField, findClass);

        String callType, cast = "", scopeVar, type;
        boolean setAndGet = !lookup.expressionResultIgnored(expr);  // If expr is not in ExpressionStmt, we must return the value

        callType = setAndGet ? "SetAndGet" : "Set";
        if (resolvedField.isStatic()) {
            scopeVar = findClass.resultingVar;
            callType += "Static";
        } else {
            scopeVar = scope;
        }

        if (resolvedField.getType().isVoid()) {
            type = "Void";
        } else if (resolvedField.getType().isPrimitive()) {
            var retType = resolvedField.getType().describe();
            type = Character.toUpperCase(retType.charAt(0)) + retType.substring(1);
            if (setAndGet) cast = "(" + retType + ")";
        } else {
            type = "Object";
        }

        var value = transpileExpression(expr.getValue());
        CPPExpression setField = new CPPExpression(cast +
                jniEnvCall(callType + type + "Field",
                        setAndGet,   // Util function take env as an argument
                        scopeVar,
                        getFieldId.resultingVar,
                        value.toString()
                ), resolvedField.getType());
        registerJniStatement(findClass, getFieldId);

        return setField;
    }

    private CPPExpression transpileUnary(UnaryExpr expr) {
        if (!(expr.getExpression() instanceof FieldAccessExpr fieldExpr) || !isModifyingUnary(expr.getOperator())) {
            return expr.getOperator().isPostfix() ?
                    new CPPExpression("%s%s", expr.calculateResolvedType(),
                            transpileExpression(expr.getExpression()), expr.getOperator().asString())
                    : new CPPExpression("%s%s", expr.calculateResolvedType(),
                    expr.getOperator().asString(), transpileExpression(expr.getExpression()));
        }

        String scope, scopeClass;
        //TODO Handle fields without scope (`this` and static)
        if (fieldExpr.getScope() instanceof ThisExpr) {
            scope = THIS_PARAM;
            scopeClass = thisType.describe();
        } else {
            var transpiled = transpileExpression(fieldExpr.getScope());
            scope = transpiled.code;
            scopeClass = transpiled.type.describe();
        }

        var resolvedField = fieldExpr.resolve().asField();
        var findClass = jniFindClass(scopeClass);
        var getFieldId = resolvedField.isStatic() ? jniGetStaticFieldId(resolvedField, findClass) :
                jniGetFieldId(resolvedField, findClass);

        String callType, cast = "", scopeVar, type;
        callType = expr.isPostfix() ? "PostfixAdd" : "PrefixAdd";
        if (resolvedField.isStatic()) {
            scopeVar = findClass.resultingVar;
            callType += "Static";
        } else {
            scopeVar = scope;
        }

        if (resolvedField.getType().isVoid()) {
            type = "Void";
        } else if (resolvedField.getType().isPrimitive()) {
            var retType = resolvedField.getType().describe();
            type = Character.toUpperCase(retType.charAt(0)) + retType.substring(1);
            cast = "(" + retType + ")";
        } else {
            type = "Object";
        }

        CPPExpression unaryOp = new CPPExpression(cast +
                jniEnvCall(callType + type + "Field", true, scopeVar, getFieldId.resultingVar,
                        expr.getOperator().name().endsWith("INCREMENT") ? "1" : "-1"
                ), resolvedField.getType());
        registerJniStatement(findClass, getFieldId);

        return unaryOp;
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

        expr.getVariables().stream().map(v ->
                v.getInitializer().map(i -> v.getNameAsString() + " = " + transpileExpression(i)).orElse(v.getNameAsString())
        ).collect(Collectors.collectingAndThen(Collectors.joining(", "), builder::append));

        //FIXME null here is explosive
        return new CPPExpression(builder.toString(), null);
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
            res = "jobject";
        }

        return new CPPExpression(res, type.resolve());
    }

    // ---------- JNI TOOLS ----------
    private String jniEnvCall(String functionName, String... params) {
        return jniEnvCall(functionName, false, params);
    }

    private String jniEnvCall(String functionName, boolean envAsArg, String... params) {
        usedUtilFunctions.add(functionName);
        return envAsArg ? "%s(%s, %s)".formatted(functionName, ENV_PARAM, String.join(", ", params))
                : "%s->%s(%s)".formatted(ENV_PARAM, functionName, String.join(", ", params));

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

    private static String getFieldSignature(ResolvedFieldDeclaration field) {
        return typeToJniSignature(field.getType().describe());
    }

    private static String uniqueMethodIdName(ResolvedMethodDeclaration method) {
        var signature = getMethodSignature(method).replaceAll("[();/]", "").replace("[", "A");

        return uniqueClassName(method.getClassName()) + "_" + method.getName() + "_" + signature;
    }

    private static String uniqueFieldIdName(ResolvedFieldDeclaration field) {
        return uniqueClassName(field.declaringType().getClassName()) + "_" + field.getName();
    }

    private static String uniqueClassName(String className) {
        return className.replace(".", "_");
    }

    private JniStatement jniGetStaticMethodId(ResolvedMethodDeclaration method, JniStatement classStatement) {
        var varName = uniqueMethodIdName(method);
        return new JniStatement("jmethodID " + varName + ";", varName + " = " +
                jniEnvCall("GetStaticMethodID", classStatement.resultingVar, "\"" + method.getName() + "\"", "\"" + getMethodSignature(method) + "\"") + ";",
                JniStatementType.GET_STATIC_METHOD_ID, varName
        );
    }

    private JniStatement jniGetMethodId(ResolvedMethodDeclaration method, JniStatement classStatement) {
        var varName = uniqueMethodIdName(method);
        return new JniStatement("jmethodID " + varName + ";", varName + " = " +
                jniEnvCall("GetMethodID", classStatement.resultingVar, "\"" + method.getName() + "\"", "\"" + getMethodSignature(method) + "\"") + ";",
                JniStatementType.GET_METHOD_ID, varName
        );
    }

    private JniStatement jniGetStaticFieldId(ResolvedFieldDeclaration field, JniStatement classStatement) {
        var varName = uniqueFieldIdName(field);
        return new JniStatement("jfieldID " + varName + ";", varName + " = " +
                jniEnvCall("GetStaticFieldID", classStatement.resultingVar, "\"" + field.getName() + "\"", "\"" + getFieldSignature(field) + "\"") + ";",
                JniStatementType.GET_STATIC_FIELD_ID, varName
        );
    }

    private JniStatement jniGetFieldId(ResolvedFieldDeclaration field, JniStatement classStatement) {
        var varName = uniqueFieldIdName(field);
        return new JniStatement("jfieldID " + varName + ";", varName + " = " +
                jniEnvCall("GetFieldID", classStatement.resultingVar, "\"" + field.getName() + "\"", "\"" + getFieldSignature(field) + "\"") + ";",
                JniStatementType.GET_FIELD_ID, varName
        );
    }

    private JniStatement jniFindClass(String name) {
        var varName = "class_" + uniqueClassName(name);
        return new JniStatement("jclass " + varName + ";", varName + " = " + jniEnvCall("FindClass", "\"" + name.replace(".", "/") + "\"") + ";",
                JniStatementType.FIND_CLASS, varName
        );
    }

    private void sortJni() {
        var temp = new ArrayList<>(this.jniStatements);
        temp.sort((a, b) -> {
            boolean aRequiresB = dependsOn(a, b);
            boolean bRequiresA = dependsOn(b, a);

            if (aRequiresB && bRequiresA)
                throw new IllegalStateException("Circular dependency detected between JNI statements:\n" + a + "\n" + b);

            if (aRequiresB) return 1;
            if (bRequiresA) return -1;
            return 0;
        });
        jniStatements.clear();
        jniStatements.addAll(temp);
    }

    private boolean dependsOn(JniStatement stmt, JniStatement dependency) {
        return stmt.initialization.matches(".*\\b" + Pattern.quote(dependency.resultingVar) + "\\b.*");
    }

    private void registerJniStatement(List<JniStatement> statements) {
        this.jniStatements.addAll(statements);
    }

    private void registerJniStatement(JniStatement... statements) {
        Collections.addAll(this.jniStatements, statements);
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
        return s.formatted(args);
    }

    private String indent(String s) {
        var r = s.indent(INDENT_LENGTH);
        if (!s.isEmpty() && !s.endsWith("\n"))
            r = r.substring(0, r.length() - 1);
        return r;
    }

    private Function<List<List<CPPStatement>>, String> formatingBlocks(String code) {
        return blocks -> {
            var parts = code.split("#");
            var builder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                builder.append(parts[i].replaceAll(" +$", "")).append(indent(statementBlockToCode(blocks.get(i))));
            }

            builder.append(parts[parts.length - 1]);

            return builder.toString();
        };
    }

    private String statementBlockToCode(List<CPPStatement> cppStatements) {
        StringJoiner joiner = new StringJoiner("\n");
        for (CPPStatement cppStatement : cppStatements) {
            String codeAsStatement = cppStatement.getCodeAsStatement();
            joiner.add(codeAsStatement);
        }
        return joiner.toString();
    }

    private String removeUnusedUtils(String contents) {
        var functionNamePattern = Pattern.compile("^.+ (\\w+)\\(.+\\) ?\\{\n");
        var result = new StringJoiner("\n\n");
        for (String function : contents.split("\n\n")) {
            if (function.trim().startsWith("//")) continue;

            var nameMatcher = functionNamePattern.matcher(function);
            if (!nameMatcher.find()) continue;
            var name = nameMatcher.group(1);

            if (usedUtilFunctions.contains(name))
                result.add(function);
        }

        return result.toString();
    }

    private boolean isModifyingUnary(UnaryExpr.Operator operator) {
        return switch (operator) {
            case POSTFIX_DECREMENT, POSTFIX_INCREMENT, PREFIX_DECREMENT, PREFIX_INCREMENT -> true;
            default -> false;
        };
    }

    private void include(Include i) {
        this.toInclude.add(i);
    }

    public enum Include {
        STRING("string.h");

        private final String file;

        Include(String file) {
            this.file = file;
        }

        public String getFile() {
            return file;
        }
    }

    public static class CPPStatement {
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

    public enum BlockType {
        IF, FOR, WHILE, SWITCH
    }

    public static class JniStatement {
        public final String initialization;
        public final String declaration;
        public final JniStatementType type;
        public final String resultingVar;

        public JniStatement(String declaration, String initialization, JniStatementType type, String resultingVar) {
            this.initialization = initialization;
            this.declaration = declaration;
            this.type = type;
            this.resultingVar = resultingVar;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            JniStatement that = (JniStatement) o;
            return resultingVar.equals(that.resultingVar);
        }

        @Override
        public int hashCode() {
            return resultingVar.hashCode();
        }
    }

    public enum JniStatementType {
        GET_FIELD_ID("GetFieldID"),
        GET_STATIC_FIELD_ID("GetStaticFieldID"),
        GET_METHOD_ID("GetMethodID"),
        GET_STATIC_METHOD_ID("GetStaticMethodID"),
        FIND_CLASS("FindClass"),
        GET_OBJECT_CLASS("GetObjectClass"),
        ;

        public final String name;

        JniStatementType(String name) {
            this.name = name;
        }
    }

    public static class CPPExpression extends CPPStatement {
        public final String code;
        public final ResolvedType type;

        public CPPExpression(String code, ResolvedType type, Object... nodes) {
            this(code.formatted(nodes), type);
        }

        public CPPExpression(String code, ResolvedType type) {
            super(code);
            this.code = code;
            this.type = type;
        }

        @Override
        public String getCodeAsStatement() {
            return code + ";";
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