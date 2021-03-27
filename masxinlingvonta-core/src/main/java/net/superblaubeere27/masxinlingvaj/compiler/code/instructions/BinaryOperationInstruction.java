package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

public class BinaryOperationInstruction extends Instruction {
    private final StackSlot lhs;
    private final StackSlot rhs;
    private final StackSlot output;
    private final BinaryOperationType type;

    public BinaryOperationInstruction(StackSlot lhs, StackSlot rhs, StackSlot output, BinaryOperationType type) {
        if (lhs.getType() != rhs.getType() || output.getType() != rhs.getType())
            throw new IllegalStateException("lhs, rhs and output have to have the same type");

        this.lhs = lhs;
        this.rhs = rhs;
        this.output = output;
        this.type = type;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();

        var lhsOperand = translatedMethod.getStack().buildStackLoad(builder, this.lhs);
        var rhsOperand = translatedMethod.getStack().buildStackLoad(builder, this.rhs);

        LLVMValueRef resultValue;

        // Is this a float operation?
        if (this.lhs.getType() == JNIType.FLOAT || this.lhs.getType() == JNIType.DOUBLE) {
            switch (this.type) {
                case ADD:
                    resultValue = LLVM.LLVMBuildFAdd(builder, lhsOperand, rhsOperand, "fadd");
                    break;
                case SUB:
                    resultValue = LLVM.LLVMBuildFSub(builder, lhsOperand, rhsOperand, "fsub");
                    break;
                case MUL:
                    resultValue = LLVM.LLVMBuildFMul(builder, lhsOperand, rhsOperand, "fmul");
                    break;
                case DIV:
                    resultValue = LLVM.LLVMBuildFDiv(builder, lhsOperand, rhsOperand, "fdiv");
                    break;
                case REM:
                    resultValue = LLVM.LLVMBuildFRem(builder, lhsOperand, rhsOperand, "frem");
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.type);
            }
        } else {
            switch (this.type) {
                case ADD:
                    resultValue = LLVM.LLVMBuildNSWAdd(builder, lhsOperand, rhsOperand, "add");
                    break;
                case SUB:
                    resultValue = LLVM.LLVMBuildNSWSub(builder, lhsOperand, rhsOperand, "sub");
                    break;
                case MUL:
                    resultValue = LLVM.LLVMBuildNSWMul(builder, lhsOperand, rhsOperand, "mul");
                    break;
                case DIV: {
                    // Throw an arithmetics exception if the divider is zero
                    block.throwIf(
                            compiler,
                            translatedMethod,
                            LLVM.LLVMBuildICmp(builder,
                                    LLVM.LLVMIntEQ,
                                    rhsOperand,
                                    LLVM.LLVMConstInt(LLVM.LLVMTypeOf(rhsOperand), 0, 0),
                                    ""),
                            "java/lang/ArithmeticException",
                            "/ by zero"
                    );

                    resultValue = LLVM.LLVMBuildSDiv(builder, lhsOperand, rhsOperand, "div");
                    break;
                }
                case REM:
                    resultValue = LLVM.LLVMBuildSRem(builder, lhsOperand, rhsOperand, "rem");
                    break;
                case SHL:
                    resultValue = LLVM.LLVMBuildShl(builder, lhsOperand, rhsOperand, "shl");
                    break;
                case SHR:
                    resultValue = LLVM.LLVMBuildAShr(builder, lhsOperand, rhsOperand, "shr");
                    break;
                case USHR:
                    resultValue = LLVM.LLVMBuildLShr(builder, lhsOperand, rhsOperand, "ushr");
                    break;
                case AND:
                    resultValue = LLVM.LLVMBuildAnd(builder, lhsOperand, rhsOperand, "and");
                    break;
                case OR:
                    resultValue = LLVM.LLVMBuildOr(builder, lhsOperand, rhsOperand, "or");
                    break;
                case XOR:
                    resultValue = LLVM.LLVMBuildXor(builder, lhsOperand, rhsOperand, "xor");
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.type);
            }
        }

        translatedMethod.getStack().buildStackStore(builder, this.output, resultValue);
    }

    public enum BinaryOperationType {
        ADD, SUB, MUL, DIV, REM, SHL, SHR, USHR, AND, OR, XOR
    }
}
