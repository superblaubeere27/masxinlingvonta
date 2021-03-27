package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import org.objectweb.asm.Opcodes;

import static org.bytedeco.llvm.global.LLVM.*;

public enum ComparisionType implements Opcodes {
    ICMP_EQ(LLVMIntEQ),
    ICMP_NE(LLVMIntNE),
    ICMP_LT(LLVMIntSLT),
    ICMP_GE(LLVMIntSGE),
    ICMP_GT(LLVMIntSGT),
    ICMP_LE(LLVMIntSLE),
    ACMP_EQ(LLVMIntNE),
    ACMP_NE(LLVMIntEQ),
    IF_NULL(LLVMIntEQ),
    IF_NOT_NULL(LLVMIntNE);

    private final int llvmPredicate;

    ComparisionType(int llvmPredicate) {
        this.llvmPredicate = llvmPredicate;
    }

    public static ComparisionType fromOpcode(int opc) {
        switch (opc) {
            case IFEQ: // visitJumpInsn
            case IF_ICMPEQ: // -
                return ICMP_EQ;
            case IFNE: // -
            case IF_ICMPNE: // -
                return ICMP_NE;
            case IFLT: // -
            case IF_ICMPLT: // -
                return ICMP_LT;
            case IFGE: // -
            case IF_ICMPGE: // -
                return ICMP_GE;
            case IFGT: // -
            case IF_ICMPGT: // -
                return ICMP_GT;
            case IFLE: // -
            case IF_ICMPLE: // -
                return ICMP_LE;
            case IF_ACMPEQ: // -
                return ACMP_EQ;
            case IF_ACMPNE: // -
                return ACMP_NE;
            case IFNONNULL: // -
                return IF_NOT_NULL;
            case IFNULL: // -
                return IF_NULL;
            default:
                throw new IllegalArgumentException("No.");
        }
    }

    public int getLLVMPredicate() {
        return llvmPredicate;
    }
}
