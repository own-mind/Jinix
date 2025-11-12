package org.jinix.plugin.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import org.jinix.plugin.MethodSourceReport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class MethodNativizer {
    private final TypeSolver typeSolver;
    private File headerFile;
    private File transpiledSourceFile;

    public MethodNativizer(TypeSolver typeSolver) {
        this.typeSolver = typeSolver;
    }

    public void nativizeReported() {
        var report = MethodSourceReport.retrieveReport();
        var transpiler = new CPPTranspiler(typeSolver, report);

        var temp = prepareTempDirectory();

        var parser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));  //TODO detect automatically
        var parsedMethods = report.getClassData().values().stream().flatMap(c -> c.nativizeMethods.stream().map(m -> Map.entry(c.name, m)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(m -> transpiler.parseMethod(m.getKey(), m.getValue(), parser), Collectors.toList())));

        this.headerFile = new File(temp, "jinix.h");
        var functionDeclarations = new HeaderGenerator().generateHeader(parsedMethods, this.headerFile);

        this.transpiledSourceFile = new File(temp, "jinix." + transpiler.getFileExtension());
        transpiler.transpile(functionDeclarations, parsedMethods, transpiledSourceFile);

        compileAndLink();
    }

    private void compileAndLink() {
        try {
            String libName = "libjinix.so";
            ProcessBuilder pb = new ProcessBuilder(
                    "gcc",
                    "-shared",
                    "-fPIC",
                    "-I" + System.getProperty("java.home") + "/include",
                    "-I" + System.getProperty("java.home") + "/include/linux",  //TODO make cross platform
                    transpiledSourceFile.getAbsolutePath(),
                    "-o",
                    new File(transpiledSourceFile.getParentFile(), libName).getAbsolutePath()
            );
            pb.redirectOutput(new File("out.txt"));
            pb.redirectError(new File("err.txt"));
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Compilation failed with exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to compile and link JNI library", e);
        }
    }

    private static File prepareTempDirectory() {
        var temp = new File(System.getProperty("user.dir") + "/.jinix/");
        temp.mkdir();

        try(var stream = Files.walk(temp.toPath())) {
            stream.map(Path::toFile)
                  .filter(f -> !f.equals(temp))
                  .forEach(File::delete);
        } catch (IOException ignored) {}

        return temp;
    }
}
