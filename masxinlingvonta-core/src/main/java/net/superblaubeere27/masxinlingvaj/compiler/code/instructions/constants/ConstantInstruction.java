package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants;

import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public abstract class ConstantInstruction extends Instruction {
    protected final StackSlot outputSlot;

    ConstantInstruction(StackSlot outputSlot) {
        this.outputSlot = outputSlot;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }
}
