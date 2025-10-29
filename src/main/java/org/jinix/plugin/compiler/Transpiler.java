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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO Transpiler should be able to transpile in parallel
public abstract class Transpiler {
    protected static final String ENV_PARAM = "env";
    protected static final String THIS_PARAM = "thisObject";
    protected static final String THIS_CLASS = "thisClass";

    protected final TypeSolver solver;
    protected final MethodSourceReport sourceReport;

    protected Transpiler(TypeSolver solver, MethodSourceReport report) {
        this.solver = solver;
        this.sourceReport = report;
    }

    protected abstract String transpileBody(MethodDeclaration method);
    protected abstract String getFileExtension();

    protected String transpileMethod(JniFunctionDeclaration declaration, MethodDeclaration method) {
        return "%s %s(JNIEnv *%s, jobject %s%s) {\n%s}".formatted(
                declaration.returnType(),
                declaration.name(),
                ENV_PARAM, THIS_PARAM,
                declaration.parameters().stream()
                        .map(p -> ", " + jniType(p.getType()) + " " + p.getName())
                        .collect(Collectors.joining()),
                transpileBody(method).indent(4)
        );
    }

    public void transpile(Map<String, List<JniFunctionDeclaration>> declarationsMap, Map<String, List<MethodDeclaration>> methodsMap, File destination) {
        try (var out = new PrintWriter(new FileWriter(destination))){
            out.println("#include \"jinix.h\"");
            out.println();

            //TODO add preamble

            for (String className : declarationsMap.keySet()) {
                var declarations = declarationsMap.get(className);
                var methods = methodsMap.get(className);
                assert declarations.size() == methods.size();

                for (int i = 0; i < declarations.size(); i++) {
                    out.println(transpileMethod(declarations.get(i), methods.get(i)));
                    out.println();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MethodDeclaration parseMethod(String methodBody, JavaParser parser) {
        // We enclose method in a class so that Java Parser could read it
        var enclosed = "class Dummy {\n" + methodBody + "\n}";

        var compilationUnit = parser.parse(enclosed).getResult().orElseThrow();

        var dummyClass = compilationUnit.getClassByName("Dummy").orElseThrow();
        return dummyClass.getMethods().getFirst();
    }

    public static String jniType(Type type) {
        if (type.isVoidType()) return "void";
        if (List.of("String", "java.lang.String").contains(type.asString())) return "jstring";
        //TODO add arrays
        if (type.isPrimitiveType()) return "j" + type.asString();
        return "jobject";
    }
}
