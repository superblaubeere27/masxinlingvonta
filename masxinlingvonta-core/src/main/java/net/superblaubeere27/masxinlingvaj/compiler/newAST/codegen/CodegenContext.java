package net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen;

import org.bytedeco.llvm.LLVM.LLVMModuleRef;

public class CodegenContext {
    private final LLVMModuleRef module;

    public CodegenContext(LLVMModuleRef module) {
        this.module = module;
    }

    public LLVMModuleRef getModule() {
        return module;
    }
}
