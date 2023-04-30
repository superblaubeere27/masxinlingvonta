package net.superblaubeere27.masxinlingvaj.compiler.jni;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;

public enum JNIType {
    VOID("void", LLVM.LLVMVoidType(), -1),
    BOOLEAN("boolean", LLVM.LLVMInt1Type(), 1),
    CHAR("char", LLVM.LLVMInt16Type(), 2),
    BYTE("byte", LLVM.LLVMInt8Type(), 1),
    SHORT("short", LLVM.LLVMInt16Type(), 2),
    INT("int", LLVM.LLVMInt32Type(), 4),
    LONG("long", LLVM.LLVMInt64Type(), 8),
    FLOAT("float", LLVM.LLVMFloatType(), 4),
    DOUBLE("double", LLVM.LLVMDoubleType(), 8),
    OBJECT("Object", LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0), 8),
    ;

    private final String displayName;
    private final LLVMTypeRef type;
    private final int sizeInBytes;

    JNIType(String displayName, LLVMTypeRef llvmVoidType, int sizeInBytes) {
        this.displayName = displayName;
        this.type = llvmVoidType;
        this.sizeInBytes = sizeInBytes;
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

    public int getSizeInBytes() {
        return sizeInBytes;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSigned() {
        return this != CHAR;
    }
}
