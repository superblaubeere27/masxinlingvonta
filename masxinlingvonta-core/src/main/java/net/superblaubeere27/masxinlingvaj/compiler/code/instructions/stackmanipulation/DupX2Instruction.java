package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.stackmanipulation;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class DupX2Instruction extends Instruction {
    private final JNIType operandTypeA;
    private final JNIType operandTypeB;
    private final JNIType operandTypeC;
    private final int stackSize;

    public DupX2Instruction(JNIType operandTypeA, JNIType operandTypeB, JNIType operandTypeC, int stackSize) {
        this.operandTypeA = operandTypeA;
        this.operandTypeB = operandTypeB;
        this.operandTypeC = operandTypeC;
        this.stackSize = stackSize;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var a = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(),
                                                           new StackSlot(this.operandTypeA, this.stackSize - 3));
        var b = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(),
                                                           new StackSlot(this.operandTypeB, this.stackSize - 2));
        var c = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(),
                                                           new StackSlot(this.operandTypeC, this.stackSize - 1));

        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeC, this.stackSize - 3),
                c
        );
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeA, this.stackSize - 2),
                a
        );
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeB, this.stackSize - 1),
                b
        );
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                new StackSlot(this.operandTypeC, this.stackSize),
                c
        );
    }
}
