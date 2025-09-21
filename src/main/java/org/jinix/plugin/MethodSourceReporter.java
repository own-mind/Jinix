package org.jinix.plugin;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jinix.plugin.MethodSourceReport.fullName;

public class MethodSourceReporter {
    // TODO doesn't allow method overload, make it happen
    private final Map<String, MethodTree> methodTrees = new ConcurrentHashMap<>();
    private final MethodScanner methodScanner = new MethodScanner();
    private final Trees trees;

    public MethodSourceReporter(ProcessingEnvironment environment) {
        this.trees = Trees.instance(environment);
    }

    public void registerSource(ExecutableElement element) {
        var methodTree = methodScanner.scan(element, trees);
        methodTrees.put(fullName(element.getEnclosingElement().toString(), methodTree.getName()), methodTree);
        System.err.println(methodTrees);
    }

    public void writeReport(){
        var report = new MethodSourceReport();
        methodTrees.forEach(report::addMethod);

        try (var stream = new ObjectOutputStream(new FileOutputStream(MethodSourceReport.REPORT_FILE))){
            stream.writeObject(report);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
