package org.jinix.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
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
        String dependencyNotation = GROUP + ":" + NAME + ":" + VERSION;
        target.getDependencies().add("implementation", dependencyNotation);
        target.getDependencies().add("annotationProcessor", dependencyNotation);

        target.getTasks().matching(t -> t.getName().equals("classes")).all(task -> {
            task.doLast(t -> {
                var outputDir = target.getLayout().getBuildDirectory().file("classes/java/main").get().getAsFile();
                var marker = new File(outputDir, ".jinix_native_transform_done");
                if (marker.exists()) return; // Already transformed

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

                try {
                    //noinspection ResultOfMethodCallIgnored
                    marker.createNewFile();
                } catch (IOException ignored) {}

                new MethodNativizer().nativizeReported();
            });
        });
    }
}