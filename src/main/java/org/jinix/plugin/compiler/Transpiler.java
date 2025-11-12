package org.jinix.plugin.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import org.jinix.plugin.MethodSourceReport;
import org.jinix.plugin.compiler.HeaderGenerator.JniFunctionDeclaration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO Transpiler should be able to transpile in parallel
public abstract class Transpiler {
    protected static final String ENV_PARAM = "env";
    protected static final String THIS_PARAM = "thisObject";

    protected final TypeSolver solver;
    protected final MethodSourceReport sourceReport;

    protected Transpiler(TypeSolver solver, MethodSourceReport report) {
        this.solver = solver;
        this.sourceReport = report;
    }

    protected abstract String transpileBody(String declaringClass, MethodDeclaration method);
    protected abstract String getFileExtension();

    protected String transpileMethod(JniFunctionDeclaration declaration, String className, MethodDeclaration method) {
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

    public void transpile(Map<String, List<JniFunctionDeclaration>> declarationsMap, Map<String, List<MethodDeclaration>> methodsMap, File destination) {
        try (var out = new PrintWriter(new FileWriter(destination))){
            out.println("#include \"jinix.h\"");
            out.println();


            try (var stream = CPPTranspiler.class.getResourceAsStream("/jinix_utils.cpp")) {
                assert stream != null;
                out.println(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                out.print("\n\n");
            }
            //TODO add preamble

            for (String className : declarationsMap.keySet()) {
                var declarations = declarationsMap.get(className);
                var methods = methodsMap.get(className);
                assert declarations.size() == methods.size();

                for (int i = 0; i < declarations.size(); i++) {
                    out.println(transpileMethod(declarations.get(i), className, methods.get(i)));
                    out.println();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MethodDeclaration parseMethod(String className, String methodName, JavaParser parser) {
        var classSource = sourceReport.getClassData().get(className).source;
        var compilationUnit = parser.parse(classSource).getResult().orElseThrow();  // TODO Cache for re-runs

        var parts = className.split("\\.");
        var dummyClass = compilationUnit.getClassByName(parts[parts.length - 1]).orElseThrow();
        return dummyClass.getMethodsByName(methodName).getFirst();
    }

    public static String jniType(Type type) {
        if (type.isVoidType()) return "void";
        if (List.of("String", "java.lang.String").contains(type.asString())) return "jstring";
        //TODO add arrays
        if (type.isPrimitiveType()) return "j" + type.asString();
        return "jobject";
    }
}
