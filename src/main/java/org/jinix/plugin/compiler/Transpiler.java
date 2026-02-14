package org.jinix.plugin.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import org.jinix.plugin.MethodSourceReport;
import org.jinix.plugin.compiler.HeaderGenerator.JniFunctionDeclaration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TODO Transpiler should be able to transpile in parallel
public abstract class Transpiler {
    protected final TypeSolver solver;
    protected final MethodSourceReport sourceReport;

    protected Transpiler(TypeSolver solver, MethodSourceReport report) {
        this.solver = solver;
        this.sourceReport = report;
    }

    protected abstract void beforeMethods(PrintWriter writer) throws Exception;
    protected abstract void afterMethods(PrintWriter writer) throws Exception;
    protected abstract String getFileExtension();
    protected abstract String transpileMethod(JniFunctionDeclaration declaration, String className, MethodDeclaration method) throws Exception;

    public void transpile(Map<String, List<JniFunctionDeclaration>> declarationsMap, Map<String, List<MethodDeclaration>> methodsMap, File destination) {
        try (var out = new PrintWriter(new FileWriter(destination))){
            List<String> transpiledMethods = new ArrayList<>();
            for (String className : declarationsMap.keySet()) {
                var declarations = declarationsMap.get(className);
                var methods = methodsMap.get(className);
                assert declarations.size() == methods.size();

                for (int i = 0; i < declarations.size(); i++) {
                    transpiledMethods.add(transpileMethod(declarations.get(i), className, methods.get(i)));
                }
            }

            //TODO add preamble
            beforeMethods(out);
            out.println();
            transpiledMethods.forEach(m -> out.println(m + "\n"));
            out.println();
            afterMethods(out);
        } catch (Exception e) {
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
