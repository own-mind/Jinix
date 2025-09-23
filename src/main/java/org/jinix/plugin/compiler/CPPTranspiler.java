package org.jinix.plugin.compiler;

import com.github.javaparser.ast.body.MethodDeclaration;

public class CPPTranspiler extends Transpiler {
    @Override
    protected String transpileBody(MethodDeclaration method) {
        return "return 1;";
    }

    @Override
    protected String getFileExtension() {
        return "cpp";
    }
}
