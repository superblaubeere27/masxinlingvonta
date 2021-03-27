package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;

public abstract class TerminatingInstruction extends Instruction {
    @Override
    public final boolean isTerminating() {
        return true;
    }
}
