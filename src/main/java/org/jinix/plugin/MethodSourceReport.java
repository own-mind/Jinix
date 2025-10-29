package org.jinix.plugin;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.sun.source.tree.MethodTree;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MethodSourceReport implements Serializable {
    @Serial
    private static final long serialVersionUID = 81262395629L;
    //TODO two compilations at the same time will result in error
    static final File REPORT_FILE = new File(System.getProperty("java.io.tmpdir"), "jinix-report.bin");

    private final Map<String, MethodData> methodData = new HashMap<>();

    public static MethodSourceReport retrieveReport(){
        if (!REPORT_FILE.exists())
            throw new IllegalStateException("No report to retrieve");

        try (var stream = new ObjectInputStream(new FileInputStream(REPORT_FILE))){
            return (MethodSourceReport) stream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMethod(String className, MethodTree methodTree){
        methodData.put(fullName(className, methodTree.getName()), new MethodData(
                className,
                methodTree.getName().toString(),
                methodTree.toString()
        ));
    }

    public void addMethod(String className, MethodData data){
        methodData.put(fullName(className, data.name), data);
    }

    static String fullName(CharSequence className, CharSequence methodName) {
        return className + "#" + methodName;
    }

    public boolean isMethodReported(String className, String name) {
        return methodData.containsKey(fullName(className, name));
    }

    public Collection<MethodData> getMethodDataList(){
        return methodData.values();
    }

    public String getMethodDeclaringClass(MethodDeclaration method) {
        //TODO argument overload support
        return methodData.values().stream().filter(m -> m.name.equals(method.getNameAsString())).findFirst()
                .map(m -> m.declaringClassName).orElse(null);
    }

    public static class MethodData implements Serializable {
        public final String declaringClassName;
        public final String name;
        public final String declaration;

        public MethodData(String declaringClassName, String name, String declaration) {
            this.declaringClassName = declaringClassName;
            this.name = name;
            this.declaration = declaration;
        }
    }
}
