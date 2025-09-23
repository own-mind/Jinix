package org.jinix.plugin.compiler;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.jinix.plugin.compiler.Transpiler.jniType;

public class HeaderGenerator {
    private StringBuilder result;
    private Map<String, List<JniFunctionDeclaration>> declarations;

    private void generateDeclarations(String originalClassName, Collection<MethodDeclaration> methods) {
        String className = originalClassName.replace('.', '_');

        // Generate methods
        for (MethodDeclaration method : methods) {
            var returnType = jniType(method.getType());
            //TODO support for overloaded methods,
            // see https://docs.oracle.com/en/java/javase/11/docs/specs/jni/design.html
            var jniName = "Java_" + className + "_" + method.getName();

            result.append("JNIEXPORT ").append(returnType).append(" JNICALL ").append(jniName).append("(JNIEnv *, jobject");

            var declaration = new JniFunctionDeclaration(method.getName().asString(), jniName, new ArrayList<>(), returnType);
            for (Parameter parameter : method.getParameters()) {
                declaration.parameters.add(parameter);
                result.append(", ").append(jniType(parameter.getType()));
            }

            declarations.computeIfAbsent(originalClassName, k -> new ArrayList<>()).add(declaration);

            result.append(");\n\n");
        }
    }

    public Map<String, List<JniFunctionDeclaration>> generateHeader(Map<String, List<MethodDeclaration>> parsedMethods, File destination) {
        this.result = new StringBuilder();
        this.declarations = new HashMap<>();

        // Standard JNI header guards
        result.append("""
        #include <jni.h>
        
        #ifndef _Jinix_Headers
        #define _Jinix_Headers
        
        #ifdef __cplusplus
        extern "C" {
        #endif
        
        """);

        parsedMethods.forEach(this::generateDeclarations);

        // Close extern "C"
        result.append("""
        #ifdef __cplusplus
        }
        #endif
        
        #endif
        """);

        try {
            Files.writeString(destination.toPath(), result.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return declarations;
    }

    public record JniFunctionDeclaration(String originalName, String name, List<Parameter> parameters, String returnType){}
}