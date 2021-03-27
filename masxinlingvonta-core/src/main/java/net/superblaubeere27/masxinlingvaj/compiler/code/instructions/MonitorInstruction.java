package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class MonitorInstruction extends Instruction {
    private final StackSlot input;
    private final boolean enter;

    public MonitorInstruction(StackSlot input, boolean enter) {
        this.input = input;
        this.enter = enter;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                this.enter ? JNIEnv.JNIEnvMethod.MonitorEnter : JNIEnv.JNIEnvMethod.MonitorExit,
                translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.input)
        );
    }
}
