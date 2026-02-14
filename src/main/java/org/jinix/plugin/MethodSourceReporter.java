package org.jinix.plugin;

import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class MethodSourceReporter {
    private final Trees trees;
    private final MethodSourceReport report = new MethodSourceReport();

    public MethodSourceReporter(ProcessingEnvironment environment) {
        this.trees = Trees.instance(environment);
    }

    // TODO doesn't allow method overload, make it happen
    public void registerSource(TypeElement classElement, ExecutableElement method) {
        var className = classElement.getQualifiedName().toString();
        report.addClassIfAbsent(className, () -> {
            try {
                return trees.getPath(classElement).getCompilationUnit().getSourceFile().getCharContent(true).toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        report.addMethod(className, method.getSimpleName().toString());
    }

    public void writeReport() {
        try (var stream = new ObjectOutputStream(new FileOutputStream(MethodSourceReport.REPORT_FILE))){
            stream.writeObject(report);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
