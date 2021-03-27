package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class ArrayLenInstruction extends Instruction {
    private final StackSlot array;
    private final StackSlot output;

    public ArrayLenInstruction(StackSlot array, StackSlot output) {
        this.array = array;
        this.output = output;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();

        var result = compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                JNIEnv.JNIEnvMethod.GetArrayLength,
                translatedMethod.getStack().buildStackLoad(builder, this.array));

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        translatedMethod.getStack().buildStackStore(builder, this.output, result);
    }
}
