package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class NullInstruction extends ConstantInstruction {
    public NullInstruction(StackSlot outputSlot) {
        super(outputSlot);
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                this.outputSlot,
                LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType())
        );
    }
}
