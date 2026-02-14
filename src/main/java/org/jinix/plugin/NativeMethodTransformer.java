package org.jinix.plugin;

import org.jinix.NativizationException;
import org.jinix.Nativize;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class NativeMethodTransformer extends ClassVisitor {
    private final MethodSourceReport report = MethodSourceReport.retrieveReport();
    private String className;

    public NativeMethodTransformer(ClassVisitor cv) {
        super(ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name.replace("/", ".");
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!report.isMethodReported(className, name)){
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        return new MethodVisitor(api, null) {
            private boolean shouldMakeNative = false;

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(Type.getDescriptor(Nativize.class))) {
                    shouldMakeNative = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public void visitEnd() {
                if (shouldMakeNative) {
                    if ((access & ACC_ABSTRACT) != 0) {
                        throw new NativizationException("Unable to nativize method '" + name + "': method is abstract");
                    } else if ((access & ACC_SYNCHRONIZED) != 0) {
                        throw new NativizationException("Unable to nativize method '" + name + "': method is synchronized");
                    }

                    // Remove method body, set native flag
                    int newAccess = access | ACC_NATIVE;
                    MethodVisitor mv = cv.visitMethod(newAccess, name, desc, signature, exceptions);
                    mv.visitEnd();
                } else {
                    MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
//                    NativeMethodTransformer.super.visitMethod(access, name, desc, signature, exceptions);
                }
            }
        };
    }
}
