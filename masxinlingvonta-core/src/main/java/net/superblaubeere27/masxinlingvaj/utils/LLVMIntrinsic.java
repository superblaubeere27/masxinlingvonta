package net.superblaubeere27.masxinlingvaj.utils;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;

public enum LLVMIntrinsic {
    LIFETIME_START("llvm.lifetime.start", new LLVMTypeRef[]{LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0)}),
    LIFETIME_END("llvm.lifetime.end", new LLVMTypeRef[]{LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0)}),
    ;

    private final String intrinsicName;
    private final LLVMTypeRef[] types;
    private int id = -1;

    LLVMIntrinsic(String intrinsicName, LLVMTypeRef[] types) {
        this.intrinsicName = intrinsicName;
        this.types = types;
    }

    public int getId() {
        if (this.id == -1) {
            this.id = LLVM.LLVMLookupIntrinsicID(this.intrinsicName, this.intrinsicName.length());
        }

        return this.id;
    }

    public String getIntrinsicName() {
        return intrinsicName;
    }

    public LLVMTypeRef[] getTypes() {
        return types;
    }
}
