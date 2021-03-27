package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.stackmanipulation;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class Dup2Instruction extends Instruction {
    private final JNIType operandTypeA;
    private final JNIType operandTypeB;
    private final int stackSize;

    public Dup2Instruction(JNIType operandTypeA, JNIType operandTypeB, int stackSize) {
        if (operandTypeA.isWide() || operandTypeB.isWide()) {
            throw new IllegalArgumentException();
        }

        this.operandTypeA = operandTypeA;
        this.operandTypeB = operandTypeB;
        this.stackSize = stackSize;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var operandA = translatedMethod.getStack().buildStackLoad(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeA, this.stackSize - 2)
        );
        var operandB = translatedMethod.getStack().buildStackLoad(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeB, this.stackSize - 1)
        );

        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeA, this.stackSize),
                operandA
        );

        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeB, this.stackSize + 1),
                operandB
        );
    }
}
