package org.jinix.plugin.compiler;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CPPTranspilerTest {
    private final CPPTranspiler transpiler = new CPPTranspiler();

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
            var a = null;
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
        auto a = nullptr;
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
            var a = (b += 1) > 0;
            int a = (int) (float) b;
            int a = a ? b : c;
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
        auto a = (b += 1) > 0;
        int a = (int)(float)b;
        int a = a ? b : c;
        i++;
        ++i;
        a = 1;
        """.trim(), transpiler.transpileBody(parsed));
    }

    @Test
    void flowControl() {
        var parsed = parse("""
        void method() {
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
        void method() {
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
            for (int a : array) {
                a = 1;
            }
        }
        """);

        assertEquals("""
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
        for (int a : array) {
            a = 1;
        }
        """.trim(), transpiler.transpileBody(parsed));
    }

    public MethodDeclaration parse(String code){
        var enclosed = "class Dummy {\n" + code + "\n}";

        var compilationUnit = StaticJavaParser.parse(enclosed);
        var dummyClass = compilationUnit.getClassByName("Dummy").orElseThrow();
        return dummyClass.getMethods().getFirst();
    }
}