package org.jinix.plugin;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MethodSourceReporter {
    // TODO doesn't allow method overload, make it happen
    private final Map<String, List<MethodTree>> methodTrees = new ConcurrentHashMap<>();
    private final MethodScanner methodScanner = new MethodScanner();
    private final Trees trees;

    public MethodSourceReporter(ProcessingEnvironment environment) {
        this.trees = Trees.instance(environment);
    }

    public void registerSource(ExecutableElement element) {
        var methodTree = methodScanner.scan(element, trees);
        methodTrees.computeIfAbsent(element.getEnclosingElement().toString(), k -> new ArrayList<>()).add(methodTree);
    }

    public void writeReport(){
        var report = new MethodSourceReport();
        methodTrees.forEach((c, ms) -> ms.forEach(m -> report.addMethod(c, m)));

        try (var stream = new ObjectOutputStream(new FileOutputStream(MethodSourceReport.REPORT_FILE))){
            stream.writeObject(report);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
