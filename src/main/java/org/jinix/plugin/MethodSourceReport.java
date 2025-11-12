package org.jinix.plugin;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MethodSourceReport implements Serializable {
    @Serial
    private static final long serialVersionUID = 81262395629L;
    //TODO two compilations at the same time will result in error
    static final File REPORT_FILE = new File(System.getProperty("java.io.tmpdir"), ".jinix-report");

    private final Map<String, ClassData> classData = new HashMap<>();

    public static MethodSourceReport retrieveReport(){
        if (!REPORT_FILE.exists())
            throw new IllegalStateException("No report to retrieve");

        try (var stream = new ObjectInputStream(new FileInputStream(REPORT_FILE))){
            return (MethodSourceReport) stream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMethod(String className, String methodName){
        this.classData.get(className).nativizeMethods.add(methodName);
    }

    public void addClassIfAbsent(String name, Supplier<String> sourceSupplier) {
        this.classData.computeIfAbsent(name, k -> new ClassData(k, sourceSupplier.get()));
    }

    public boolean isMethodReported(String className, String name) {
        return classData.containsKey(className) && classData.get(className).nativizeMethods.contains(name);
    }

    public Map<String, ClassData> getClassData() {
        return classData;
    }

    public static class ClassData implements Serializable {
        public final String name;
        public final String source;
        public final List<String> nativizeMethods = new ArrayList<>();

        public ClassData(String name, String source) {
            this.name = name;
            this.source = source;
        }
    }
}
