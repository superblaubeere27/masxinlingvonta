package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.global.LLVM;

public class NumberCompareInstruction extends Instruction {
    private final StackSlot lhs;
    private final StackSlot rhs;
    private final StackSlot output;
    private final boolean greaterOnNaN;

    public NumberCompareInstruction(StackSlot lhs, StackSlot rhs, StackSlot output, boolean greaterOnNaN) {
        if (lhs.getType() != rhs.getType() || output.getType() != JNIType.INT)
            throw new IllegalArgumentException("Invalid stack inputs / outputs");

        this.lhs = lhs;
        this.rhs = rhs;
        this.output = output;
        this.greaterOnNaN = greaterOnNaN;
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

        if (this.lhs.getType().isFloatingPoint()) {
            LLVMUtils.buildIfElse(
                    translatedMethod,
                    LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealUNO, lhsOperand, rhsOperand, "is_nan"),
                    () -> {
                        // One of the numbers is NaN
                        translatedMethod.getStack().buildStackStore(builder,
                                this.output,
                                LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), this.greaterOnNaN ? 1 : -1, 0));
                    },
                    () -> {
                        LLVMUtils.buildIfElse(
                                translatedMethod,
                                LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOEQ, lhsOperand, rhsOperand, "is_eq"),
                                () -> {
                                    // Both numbers are equal
                                    translatedMethod.getStack().buildStackStore(builder,
                                            this.output,
                                            LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 0, 0));
                                },
                                () -> {

                                    translatedMethod.getStack().buildStackStore(
                                            builder,
                                            this.output,
                                            LLVM.LLVMBuildSelect(
                                                    builder,
                                                    LLVM.LLVMBuildFCmp(builder,
                                                            LLVM.LLVMRealOGT,
                                                            lhsOperand,
                                                            rhsOperand,
                                                            "is_gt"),
                                                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 1),
                                                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), -1, 1),
                                                    ""
                                            )
                                    );
                                }
                        );
                    }
            );
        } else {
            LLVMUtils.buildIfElse(
                    translatedMethod,
                    LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, lhsOperand, rhsOperand, "is_eq"),
                    () -> {
                        // Both numbers are equal
                        translatedMethod.getStack().buildStackStore(builder,
                                this.output,
                                LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 0, 0));
                    },
                    () -> translatedMethod.getStack().buildStackStore(
                            builder,
                            this.output,
                            LLVM.LLVMBuildSelect(
                                    builder,
                                    LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGT, lhsOperand, rhsOperand, "is_gt"),
                                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 1),
                                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), -1, 1),
                                    ""
                            )
                    )
            );
        }
    }
}
