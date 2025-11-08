package org.jinix.plugin.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.jinix.plugin.MethodSourceReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CPPTranspilerTest {
    private final CPPTranspiler transpiler;
    private final SymbolResolver resolver;

    {
        var report = new MethodSourceReport();
        report.addMethod(this.getClass().getName(), new MethodSourceReport.MethodData(this.getClass().getName(), "method", ""));
        var solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver("src/test/java"));
        this.transpiler = new CPPTranspiler(solver, report);
        this.resolver = new JavaSymbolSolver(solver);
    }


    @Test
    void declarationsAndLiterals() {
        var parsed = parse("""
        void method() {
            var a = 0;
            String a;
            int a = 1;
            long a = 23L;
            float a = 0.1;
            double a = 0.23d;
            char a = 'a';
            boolean a = false;
            String a = null;
            String a = "val\\"ue";
            String a = \"""
                First
                Second
                Third
                \""";
            final int a;
            int a, b;
            int a = 0, b = 1;
        }
        """);

        assertEquals("""
        auto a = 0;
        std::string a;
        int a = 1;
        long a = 23L;
        float a = 0.1;
        double a = 0.23d;
        char a = 'a';
        bool a = false;
        std::string a = nullptr;
        std::string a = "val\\"ue";
        std::string a = "First\\
        Second\\
        Third\\
        ";
        const int a;
        int a, b;
        int a = 0, b = 1;
        """.trim(), transpiler.transpileBody(parsed));
    }

    @Test
    void expressions() {
        var parsed = parse("""
        void method() {
            int a = 3 + 5;
            int a = 5 + (3 - 4) * 5;
            boolean a = !false;
            boolean a = !!!false + ++a;
            int b = 1;
            var a = (b += 1) > 0;
            int a = (int) (float) b;
            int a = a ? b : a;
            int i = 0;
            i++;
            ++i;
            a = 1;
        }
        """);

        assertEquals("""
        int a = 3 + 5;
        int a = 5 + (3 - 4) * 5;
        bool a = !false;
        bool a = !!!false + ++a;
        int b = 1;
        auto a = (b += 1) > 0;
        int a = (int)(float)b;
        int a = a ? b : a;
        int i = 0;
        i++;
        ++i;
        a = 1;
        """.trim(), transpiler.transpileBody(parsed));
    }

    @Test
    void flowControl() {
        var parsed = parse("""
        void method() {
            int a = 0;
            if (a == 3) a = 1;
            if (a == 3) {
                a = 1;
            }
            if (a == 3) {
                a = 1;
            } else { }
            if (a == 3) {
                a = 1;
            } else if (a == 3) {
                a = 1;
            } else { }
            break;
            continue;
            return;
            return a;
            throw a;
            switch (a) {
                case "1" -> a = 1;
                case "2" -> {}
                case "3":
                    a = 1;
                    break;
                case "4":
                    a = 1;
                default -> throw a;
            }
        }
        """);


        assertEquals("""
        int a = 0;
        if (a == 3) {
            a = 1;
        }
        if (a == 3) {
            a = 1;
        }
        if (a == 3) {
            a = 1;
        } else {
        
        }
        if (a == 3) {
            a = 1;
        } else {
            if (a == 3) {
                a = 1;
            } else {
           \s
            }
        }
        break;
        continue;
        return;
        return a;
        throw a;
        switch (a) {
        case "1":
            a = 1;
        case "2":
        case "3":
            a = 1;
            break;
        case "4":
            a = 1;
        default:
            throw a;
        }
        """.trim(), transpiler.transpileBody(parsed));
    }


    @Test
    void loops() {
        var parsed = parse("""
        public int[] array;  // declared here to avoid transpilation
        
        void method() {
            int a = 0;
            int b = 0;
            while (true) {
                a = 1;
            }
            do {
                a = 1;
            } while(true);
            while (true) {
                while (true) {
                    a = 1;
                }
            }
            for (int i = 0, b = 0; i < a; i++, b = b * 2) {
                a = 1;
            }
            for (i = 0, b = 0;;);
            for (;;);
            for (int c : array) {
                a = 1;
            }
        }
        """);

        assertEquals("""
        int a = 0;
        int b = 0;
        while (true) {
            a = 1;
        }
        do {
            a = 1;
        } while (true);
        while (true) {
            while (true) {
                a = 1;
            }
        }
        for (int i = 0, b = 0; i < a; i++, b = b * 2) {
            a = 1;
        }
        for (i = 0, b = 0; ; ) {
            ;
        }
        for (; ; ) {
            ;
        }
        for (int c : array) {
            a = 1;
        }
        """.trim(), transpiler.transpileBody(parsed));
    }

    @Test
    void methodsAndFields() {
        //TODO Add nativized calls for "a.nativeCall()". You need to have a mechanism to know (if possible),
        // if "a" is certain class that has nativized method, ahead of time if possible (maybe with annotation) or in runtime (less favorable)
        var parsed = parse("""
        public int a = 1;
        public int b = 2;
        @Nativize
        void nativizedCall(){}
        void thisCall(){}
        void call(){}
        int withParams(int a, int b){ return a + b; }
        class A {
            public static int staticField = 0;
            public int a = 1;
            static void staticCall();
            static int staticCall(int a, long b);
            void call(){}
        }
        
        void method() {
            thisCall();
            this.call();
            var result = withParams(0, 1);
            withParams(withParams(1, 2), 3);
            A a;
            a.call();
            A.staticCall();
            var result = A.staticCall(0, 1);
            this.a = 1;
            this.a = this.b;
            A.staticField = 1;
            a.a = 1;
            var result = A.staticField;
        }
        """);
//            this.vararg(1, 2, 3);
//            this.nativizedCall();
//            var result = withParamsNativized(a, b);
//            A.nativeStaticCall();
//            this.nativizedVararg(1, 2, 3);
//
//        }
//        """);

        assertEquals("""
        jclass class_org_jinix_plugin_compiler_CPPTranspilerTest = (*env)->FindClass("org/jinix/plugin/compiler/CPPTranspilerTest");
        jmethodID Dummy_thisCall_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "thisCall", "()V");
        jmethodID Dummy_call_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call", "()V");
        jmethodID Dummy_withParams_III = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "withParams", "(II)I");
        jclass class_Dummy_A = (*env)->FindClass("Dummy/A");
        jmethodID Dummy_A_call_V = (*env)->GetMethodID(class_Dummy_A, "call", "()V");
        jmethodID Dummy_A_staticCall_V = (*env)->GetStaticMethodID(class_Dummy_A, "staticCall", "()V");
        jmethodID Dummy_A_staticCall_IJI = (*env)->GetStaticMethodID(class_Dummy_A, "staticCall", "(IJ)I");
        jfieldID Dummy_a = (*env)->GetFieldID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "a", "I");
        jfieldID Dummy_b = (*env)->GetFieldID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "b", "I");
        jfieldID Dummy_A_staticField = (*env)->GetStaticFieldID(class_Dummy_A, "staticField", "I");
        jfieldID Dummy_A_a = (*env)->GetFieldID(class_Dummy_A, "a", "I");
        (*env)->CallVoidMethod(thisObject, Dummy_thisCall_V);
        (*env)->CallVoidMethod(thisObject, Dummy_call_V);
        auto result = (int)(*env)->CallIntMethod(thisObject, Dummy_withParams_III, 0, 1);
        (int)(*env)->CallIntMethod(thisObject, Dummy_withParams_III, (int)(*env)->CallIntMethod(thisObject, Dummy_withParams_III, 1, 2), 3);
        jobject a;
        (*env)->CallVoidMethod(a, Dummy_A_call_V);
        (*env)->CallStaticVoidMethod(class_Dummy_A, Dummy_A_staticCall_V);
        auto result = (int)(*env)->CallStaticIntMethod(class_Dummy_A, Dummy_A_staticCall_IJI, 0, 1);
        (*env)->SetIntField(thisObject, Dummy_a, 1);
        (*env)->SetIntField(thisObject, Dummy_a, (int)(*env)->GetIntField(thisObject, Dummy_b));
        (*env)->SetStaticIntField(class_Dummy_A, Dummy_A_staticField, 1);
        (*env)->SetIntField(a, Dummy_A_a, 1);
        auto result = (int)(*env)->GetStaticIntField(class_Dummy_A, Dummy_A_staticField);
        """.trim(), transpiler.transpileBody(parsed));
    }

    @Test
    void jniFunction() {
        inheritanceTest("int a = #;");
        inheritanceTest("int a = call(#);");
        inheritanceTest("int a = # + 1;");
        inheritanceTest("int a = 1 + #;");
        inheritanceTest("int a = !#;");
        inheritanceTest("int a = (int)#;");
        inheritanceTest("int a = # ? 1 : 0;");
        inheritanceTest("int a = true ? # : 0;");
        inheritanceTest("int a = true ? 1 : #;");
        inheritanceTest("int a = (#);");

        inheritanceTest("if(#) {}");
        inheritanceTest("if(#) {} else {}");
        inheritanceTest("switch(#) {}");
        inheritanceTest("while(#) {}");
        inheritanceTest("do {} while(#);");
        inheritanceTest("for(#;;) {}");
        inheritanceTest("for(;#;) {}");
        inheritanceTest("for(;;#) {}");
        inheritanceTest("for(#;#;#) {}");
        inheritanceTest("return #;");
        inheritanceTest("throw #;");

        inheritanceTest("call(#);");
        inheritanceTest("this.field = (#);", true);

        //TODO test for try-catch-finally
        var parsed = parse("""
        void call1(){}
        void call2(){}
        void call3(){}
        void call4(){}
        void call5(){}
        
        void method() {
            call1();
            if(true) {
                call2();
                call1();
            } else if(true) {
                call2();
            } else {
                call2();
                call3();
            }
        
            if(true) {
                call5();
            } else {
                call5();
            }
        
            switch("a"){
                case "a" -> call3();
                case "a" -> call2();
            }
        
            if(true) {
                while(true){ call4(); }
            }
        }
        """);

        assertEquals("""
        jclass class_org_jinix_plugin_compiler_CPPTranspilerTest = (*env)->FindClass("org/jinix/plugin/compiler/CPPTranspilerTest");
        jmethodID Dummy_call5_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call5", "()V");
        jmethodID Dummy_call2_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call2", "()V");
        jmethodID Dummy_call1_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call1", "()V");
        (*env)->CallVoidMethod(thisObject, Dummy_call1_V);
        if (true) {
            (*env)->CallVoidMethod(thisObject, Dummy_call2_V);
            (*env)->CallVoidMethod(thisObject, Dummy_call1_V);
        } else {
            if (true) {
                (*env)->CallVoidMethod(thisObject, Dummy_call2_V);
            } else {
                jmethodID Dummy_call3_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call3", "()V");
                (*env)->CallVoidMethod(thisObject, Dummy_call2_V);
                (*env)->CallVoidMethod(thisObject, Dummy_call3_V);
            }
        }
        if (true) {
            (*env)->CallVoidMethod(thisObject, Dummy_call5_V);
        } else {
            (*env)->CallVoidMethod(thisObject, Dummy_call5_V);
        }
        switch ("a") {
        case "a":
            jmethodID Dummy_call3_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call3", "()V");
            (*env)->CallVoidMethod(thisObject, Dummy_call3_V);
        case "a":
            (*env)->CallVoidMethod(thisObject, Dummy_call2_V);
        }
        if (true) {
            jmethodID Dummy_call4_V = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call4", "()V");
            while (true) {
                (*env)->CallVoidMethod(thisObject, Dummy_call4_V);
            }
        }""", transpiler.transpileBody(parsed));
    }

    private void inheritanceTest(String code) {
        inheritanceTest(code, false);
    }

    private void inheritanceTest(String code, boolean isContains) {
        code = """
        int call(int a) { return a; }
        int field = 1;
        void method() {
            %s
        }
        """.formatted(code.replace("#", "call(1)"));
        var result = transpiler.transpileBody(parse(code));

        if (isContains) {
            assertTrue(result.contains("jclass class_org_jinix_plugin_compiler_CPPTranspilerTest = (*env)->FindClass(\"org/jinix/plugin/compiler/CPPTranspilerTest\");")
                    && result.contains("jmethodID Dummy_call_II = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, \"call\", \"(I)I\");"), result);
        } else {
            assertTrue(result.startsWith("""
            jclass class_org_jinix_plugin_compiler_CPPTranspilerTest = (*env)->FindClass("org/jinix/plugin/compiler/CPPTranspilerTest");
            jmethodID Dummy_call_II = (*env)->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call", "(I)I");"""), result);
        }
    }
    // TODO add test for jni optimization Check that all statements and expression inherit properly
    //  and that loops handle optimization properly

    public MethodDeclaration parse(String code){
        var enclosed = "class Dummy {\n" + code + "\n}";

        var parser = new JavaParser(new ParserConfiguration().setSymbolResolver(this.resolver).setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        var compilationUnit = parser.parse(enclosed).getResult().orElseThrow();
        var dummyClass = compilationUnit.getClassByName("Dummy").orElseThrow();
        return dummyClass.getMethods().stream().filter(m -> m.getNameAsString().equals("method")).findFirst().orElseThrow();
    }
}