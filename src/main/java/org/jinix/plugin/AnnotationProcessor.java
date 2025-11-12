package org.jinix.plugin;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.jinix.Nativize")
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {
    private MethodSourceReporter methodModifier;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.methodModifier = new MethodSourceReporter(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment environment) {
        for (TypeElement element : annotations){
            environment.getElementsAnnotatedWith(element)
                    .forEach(e -> {
                        if (e instanceof ExecutableElement executableElement)
                            methodModifier.registerSource((TypeElement) executableElement.getEnclosingElement(), executableElement);
                    });
        }

        methodModifier.writeReport(); // TODO inefficient, writing on every class
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return processingEnv.getSourceVersion();
    }
}
