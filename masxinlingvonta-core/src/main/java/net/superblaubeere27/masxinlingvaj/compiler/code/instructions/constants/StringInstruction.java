package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class StringInstruction extends ConstantInstruction {
    private final String cst;

    public StringInstruction(StackSlot outputSlot, String cst) {
        super(outputSlot);

        this.cst = cst;
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
                compiler.getJni().getJniEnv().callEnvironmentMethod(
                        translatedMethod, translatedMethod.getEnvPtr(),
                        JNIEnv.JNIEnvMethod.NewStringUTF,
                        LLVM.LLVMBuildGlobalStringPtr(translatedMethod.getLlvmBuilder(), this.cst, "ldc")
                )
        );
    }
}
