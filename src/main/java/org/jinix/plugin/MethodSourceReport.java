package org.jinix.plugin;

import com.sun.source.tree.MethodTree;

import java.io.*;
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
        REPORT_FILE.deleteOnExit();

        try (var stream = new ObjectInputStream(new FileInputStream(REPORT_FILE))){
            return (MethodSourceReport) stream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMethod(String path, MethodTree methodTree){
        methodData.put(path, new MethodData(
                methodTree.getName().toString(),
                methodTree.getParameters().stream().map(v -> new String[]{ v.getType().toString(), v.getName().toString() }).toArray(String[][]::new),
                methodTree.getReturnType().toString(),
                methodTree.getBody().toString()
        ));
    }

    static String fullName(CharSequence className, CharSequence methodName) {
        return className + "#" + methodName;
    }

    public boolean isMethodReported(String className, String name) {
        return methodData.containsKey(fullName(className, name));
    }

    public static class MethodData implements Serializable {
        public final String name;
        public final String[][] parameters;  // Type, name
        public final String returnType;
        public final String body;

        private MethodData(String name, String[][] parameters, String returnType, String body) {
            this.name = name;
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
        }
    }
}
