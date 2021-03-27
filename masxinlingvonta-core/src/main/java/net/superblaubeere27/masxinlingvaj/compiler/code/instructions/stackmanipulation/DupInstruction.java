package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.stackmanipulation;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class DupInstruction extends Instruction {
    private final StackSlot input;
    private final StackSlot output;

    public DupInstruction(StackSlot input, StackSlot output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                this.output,
                translatedMethod.getStack().buildStackLoad(
                        translatedMethod.getLlvmBuilder(),
                        this.input
                )
        );
    }
}
