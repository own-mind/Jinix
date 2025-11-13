package org.jinix.plugin.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.codehaus.groovy.control.io.NullWriter;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CPPTranspilerTest {
    private final CPPTranspiler transpiler;
    private final SymbolResolver resolver;

    {
        var solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver("src/test/java"));
        this.transpiler = new CPPTranspiler(solver, null);
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
        """.trim(), transpiler.transpileBody(this.getClass().getName(), parsed));
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
        """.trim(), transpiler.transpileBody(this.getClass().getName(), parsed));
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
        """.trim(), transpiler.transpileBody(this.getClass().getName(), parsed));
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
        """.trim(), transpiler.transpileBody(this.getClass().getName(), parsed));
    }

    @Test
    void methodsAndFields() throws Exception {
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
            var result = (this.a = 1);
            ++this.a;
            this.a++;
            var result = -this.a;
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
        env->CallVoidMethod(thisObject, Dummy_thisCall_V);
        env->CallVoidMethod(thisObject, Dummy_call_V);
        auto result = (int)env->CallIntMethod(thisObject, Dummy_withParams_III, 0, 1);
        (int)env->CallIntMethod(thisObject, Dummy_withParams_III, (int)env->CallIntMethod(thisObject, Dummy_withParams_III, 1, 2), 3);
        jobject a;
        env->CallVoidMethod(a, Dummy_A_call_V);
        env->CallStaticVoidMethod(class_Dummy_A, Dummy_A_staticCall_V);
        auto result = (int)env->CallStaticIntMethod(class_Dummy_A, Dummy_A_staticCall_IJI, 0, 1);
        env->SetIntField(thisObject, Dummy_a, 1);
        auto result = ((int)SetAndGetIntField(env, thisObject, Dummy_a, 1));
        (int)PrefixAddIntField(env, thisObject, Dummy_a, 1);
        (int)PostfixAddIntField(env, thisObject, Dummy_a, 1);
        auto result = -(int)env->GetIntField(thisObject, Dummy_a);
        env->SetIntField(thisObject, Dummy_a, (int)env->GetIntField(thisObject, Dummy_b));
        env->SetStaticIntField(class_Dummy_A, Dummy_A_staticField, 1);
        env->SetIntField(a, Dummy_A_a, 1);
        auto result = (int)env->GetStaticIntField(class_Dummy_A, Dummy_A_staticField);
        """.trim(), transpiler.transpileBody(this.getClass().getName(), parsed));

        transpiler.beforeMethods(new PrintWriter(new NullWriter()));
        assertEquals("""
        class_org_jinix_plugin_compiler_CPPTranspilerTest = env->FindClass("org/jinix/plugin/compiler/CPPTranspilerTest");
        Dummy_thisCall_V = env->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "thisCall", "()V");
        Dummy_call_V = env->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "call", "()V");
        Dummy_withParams_III = env->GetMethodID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "withParams", "(II)I");
        class_Dummy_A = env->FindClass("Dummy/A");
        Dummy_A_call_V = env->GetMethodID(class_Dummy_A, "call", "()V");
        Dummy_A_staticCall_V = env->GetStaticMethodID(class_Dummy_A, "staticCall", "()V");
        Dummy_A_staticCall_IJI = env->GetStaticMethodID(class_Dummy_A, "staticCall", "(IJ)I");
        Dummy_a = env->GetFieldID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "a", "I");
        Dummy_b = env->GetFieldID(class_org_jinix_plugin_compiler_CPPTranspilerTest, "b", "I");
        Dummy_A_staticField = env->GetStaticFieldID(class_Dummy_A, "staticField", "I");
        Dummy_A_a = env->GetFieldID(class_Dummy_A, "a", "I");
        """.trim(), transpiler.jniStatements.stream().map(s -> s.initialization).collect(Collectors.joining("\n")));
    }

    public MethodDeclaration parse(String code){
        var enclosed = "class Dummy {\n" + code + "\n}";

        var parser = new JavaParser(new ParserConfiguration().setSymbolResolver(this.resolver).setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        var compilationUnit = parser.parse(enclosed).getResult().orElseThrow();
        var dummyClass = compilationUnit.getClassByName("Dummy").orElseThrow();
        return dummyClass.getMethods().stream().filter(m -> m.getNameAsString().equals("method")).findFirst().orElseThrow();
    }
}