package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class UnaryInstruction extends Instruction {
    private final StackSlot inputSlot;
    private final StackSlot outputSlot;
    private final UnaryOperationType type;

    public UnaryInstruction(StackSlot inputSlot, StackSlot outputSlot, UnaryOperationType type) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.type = type;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var operand = translatedMethod.getStack().buildStackLoad(builder, this.outputSlot);

        if (this.type == UnaryOperationType.NEG) {
            if (this.inputSlot.getType() == JNIType.FLOAT || this.inputSlot.getType() == JNIType.DOUBLE) {
                translatedMethod.getStack().buildStackStore(
                        builder,
                        this.outputSlot,
                        LLVM.LLVMBuildFNeg(builder, operand, "fneg")
                );
            } else {
                translatedMethod.getStack().buildStackStore(
                        builder,
                        this.outputSlot,
                        LLVM.LLVMBuildNSWNeg(builder, operand, "neg")
                );
            }
        } else {
            throw new IllegalStateException("Unexpected value: " + this.type);
        }
    }

    public enum UnaryOperationType {
        NEG
    }
}
