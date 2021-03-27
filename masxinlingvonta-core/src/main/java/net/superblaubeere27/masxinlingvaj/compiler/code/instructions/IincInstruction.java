package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class IincInstruction extends Instruction {
    private final int idx;
    private final int value;

    public IincInstruction(int idx, int value) {
        this.idx = idx;
        this.value = value;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();

        var stackSlot = new StackSlot(JNIType.INT, this.idx);

        var increased = LLVM.LLVMBuildAdd(builder,
                translatedMethod.getStack().buildLocalLoad(builder, stackSlot),
                LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), this.value, 1),
                "iinc");

        translatedMethod.getStack().buildLocalStore(builder, stackSlot, increased);
    }
}
