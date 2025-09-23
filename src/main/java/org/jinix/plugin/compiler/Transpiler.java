package org.jinix.plugin.compiler;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.List;

public abstract class Transpiler {
    protected MethodDeclaration parseMethod(String methodBody){
        // We enclose method in a class so that Java Parser could read it
        var enclosed = "class Dummy {\n" + methodBody + "\n}";

        var compilationUnit = StaticJavaParser.parse(enclosed);
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
