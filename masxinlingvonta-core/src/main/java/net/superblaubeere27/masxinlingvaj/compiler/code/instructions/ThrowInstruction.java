package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches.TerminatingInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class ThrowInstruction extends TerminatingInstruction {
    private final StackSlot exception;

    public ThrowInstruction(StackSlot exception) {
        this.exception = exception;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var exceptionBlock = block.getExceptionBlock(compiler, translatedMethod);

        compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                JNIEnv.JNIEnvMethod.Throw,
                translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.exception)
        );

        LLVM.LLVMBuildBr(translatedMethod.getLlvmBuilder(), exceptionBlock);
    }
}
