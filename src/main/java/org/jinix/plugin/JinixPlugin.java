package org.jinix.plugin;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.jinix.NativizationException;
import org.jinix.plugin.compiler.MethodNativizer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class JinixPlugin implements Plugin<Project> {
    private static final String GROUP = "org.jinix";
    private static final String NAME = "jinix-plugin";
    private static final String VERSION = "0.0.1"; // TODO load from build

    @Override
    public void apply(Project target) {
        if (GROUP.equals(target.getGroup())) return;

        String dependencyNotation = GROUP + ":" + NAME + ":" + VERSION;
        target.getDependencies().add("implementation", dependencyNotation);
        target.getDependencies().add("annotationProcessor", dependencyNotation);

        target.getTasks().matching(t -> t.getName().equals("classes")).all(task -> task.doLast(t -> {
            var outputDir = target.getLayout().getBuildDirectory().file("classes/java/main").get().getAsFile();
            //TODO skip if there are no changes
//                var marker = new File(outputDir, ".jinix_native_transform_done");
//                if (marker.exists()) return; // Already transformed

            target.fileTree(outputDir, spec -> spec.include("**/*.class")).forEach(classFile -> {
                try {
                    byte[] original = Files.readAllBytes(classFile.toPath());
                    var reader = new ClassReader(original);
                    var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

                    var transformer = new NativeMethodTransformer(writer);
                    reader.accept(transformer, 0);

                    Files.write(classFile.toPath(), writer.toByteArray());
                } catch (IOException e) {
                    throw new NativizationException("Failed to transform " + classFile, e);
                }
            });

            var symbolSolver = setupTypeSolver(target);
            new MethodNativizer(symbolSolver).nativizeReported();

//                try {
//                    noinspection ResultOfMethodCallIgnored
//                    marker.createNewFile();
//                } catch (IOException ignored) {}
        }));
    }

    private TypeSolver setupTypeSolver(Project target) {
        var solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());   // Java's libraries
        solver.add(new JavaParserTypeSolver(target.file("src/main/java")));

        // Adding dependencies to solver
        target.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets().forEach(sourceSet -> {
                    try {
                        for (File f : sourceSet.getCompileClasspath().getFiles()) {
                            solver.add(new JarTypeSolver(f));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        return solver;
    }
}