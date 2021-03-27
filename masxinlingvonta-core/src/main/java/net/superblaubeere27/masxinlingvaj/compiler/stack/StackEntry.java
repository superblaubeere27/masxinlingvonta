package net.superblaubeere27.masxinlingvaj.compiler.stack;

import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class StackEntry {
    private final LLVMValueRef allocatedValue;

    StackEntry(LLVMValueRef allocatedValue) {
        this.allocatedValue = allocatedValue;
    }

    public LLVMValueRef getAllocatedValue() {
        return allocatedValue;
    }
}
