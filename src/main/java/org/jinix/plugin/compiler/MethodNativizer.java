package org.jinix.plugin.compiler;

import org.gradle.internal.impldep.org.apache.commons.io.file.CleaningPathVisitor;
import org.jetbrains.annotations.NotNull;
import org.jinix.plugin.MethodSourceReport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class MethodNativizer {
    private File headerFile;

    public void nativizeReported(){
        var report = MethodSourceReport.retrieveReport();
        var transpiler = new CPPTranspiler();

        var temp = prepareTempDirectory();

        var parsedMethods = report.getMethodDataList().stream()
                .collect(Collectors.groupingBy(m -> m.declaringClassName,
                        Collectors.mapping(m -> transpiler.parseMethod(m.declaration), Collectors.toList())));

        this.headerFile = new File(temp, "Jinix.h");
        var functionDeclarations = new HeaderGenerator().generateHeader(parsedMethods, this.headerFile);
    }

    private static File prepareTempDirectory() {
        var temp = new File(".jinix/");
        temp.mkdir();

        try(var stream = Files.walk(temp.toPath())) {
            stream.map(Path::toFile)
                  .filter(f -> !f.equals(temp))
                  .forEach(File::delete);
        } catch (IOException ignored) {}

        return temp;
    }
}
