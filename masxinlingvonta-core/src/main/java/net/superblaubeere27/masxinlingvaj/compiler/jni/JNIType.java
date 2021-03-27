package net.superblaubeere27.masxinlingvaj.compiler.jni;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;

public enum JNIType {
    VOID(LLVM.LLVMVoidType()),
    BOOLEAN(LLVM.LLVMInt1Type()),
    CHAR(LLVM.LLVMInt16Type()),
    BYTE(LLVM.LLVMInt8Type()),
    SHORT(LLVM.LLVMInt16Type()),
    INT(LLVM.LLVMInt32Type()),
    LONG(LLVM.LLVMInt64Type()),
    FLOAT(LLVM.LLVMFloatType()),
    DOUBLE(LLVM.LLVMDoubleType()),
    OBJECT(LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0)),
    ;

    private final LLVMTypeRef type;

    JNIType(LLVMTypeRef llvmVoidType) {
        this.type = llvmVoidType;
    }

    public LLVMTypeRef getLLVMType() {
        return type;
    }

    public boolean isFloatingPoint() {
        return this == FLOAT || this == DOUBLE;
    }

    public JNIType getStackStorageType() {
        switch (this) {
            case BOOLEAN:
            case CHAR:
            case BYTE:
            case SHORT:
                return INT;
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case OBJECT:
                return this;
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }

    public boolean isWide() {
        switch (this) {
            case VOID:
                throw new IllegalArgumentException("What?");
            case BOOLEAN:
            case CHAR:
            case BYTE:
            case SHORT:
            case INT:
            case FLOAT:
            case OBJECT:
                return false;
            case LONG:
            case DOUBLE:
                return true;
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
