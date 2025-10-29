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
        void method() {
            thisCall();
            this.call();
            var result = withParams(a, b);
        }
        """);

//            this.nativizedCall();
//            var result = withParamsNativized(a, b);
//            a.call();
//            A.staticCall();
//            A.nativeStaticCall();
//            var result = A.staticCall(a, b);
//
//            this.a = 1;
//            this.a = this.b;
//            a.a = 1;
//            A.staticField = 1;
//        }
//        """);

        //TODO method id and class optimizations: if class is used or method is called often,
        // like in a loop or if annotated as such,
        // then it is better to get its handle/id at the start of the method body,
        // or at the logical block like if statement.
        // Straight forward optimization: do all method handles, even single ones, at the start of logic block.
        // The goal here is to not call them when method/class may be unreachable because of if statement.
        // And don't forget that if there are duplicate method id calls,
        // then you could do them in the upper level once, method level being the top one.
        // If this implementation is used,
        // then it is better alternative to current placement of thisClass call at the beginning of method body,
        // since it could be unused because of if statement.
        // Could require rewrite of transpilation storing logic, as list of independent blocks or trees,
        // that allow insertion of optimization code in-between.

        assertEquals("""
        jclass thisClass = (*env)->GetObjectClass(env, thisObject);
        (*env)->CallVoidMethod(env, thisObject, (*env)->GetMethodID(env, thisClass, "thisCall", "()V"));
        (*env)->CallVoidMethod(env, thisObject, (*env)->GetMethodID(env, thisClass, "call", "()V"));
        """.trim(), transpiler.transpileBody(parsed));
    }

    public MethodDeclaration parse(String code){
        var enclosed = "class Dummy {\n" + code + "\n}";

        var parser = new JavaParser(new ParserConfiguration().setSymbolResolver(this.resolver).setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        var compilationUnit = parser.parse(enclosed).getResult().orElseThrow();
        var dummyClass = compilationUnit.getClassByName("Dummy").orElseThrow();
        return dummyClass.getMethods().getFirst();
    }
}