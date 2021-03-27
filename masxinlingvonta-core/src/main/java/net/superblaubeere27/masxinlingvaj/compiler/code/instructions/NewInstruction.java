package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class NewInstruction extends Instruction {
    private final String typeName;
    private final StackSlot output;


    public NewInstruction(String typeName, StackSlot output) {
        this.typeName = typeName;
        this.output = output;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var llvmValueRef = translatedMethod.buildFindClass(compiler.getJni(), this.typeName);

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        var alloc = compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                JNIEnv.JNIEnvMethod.AllocObject,
                llvmValueRef
        );

        translatedMethod.getStack().buildStackStore(translatedMethod.getLlvmBuilder(), this.output, alloc);

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }
}
