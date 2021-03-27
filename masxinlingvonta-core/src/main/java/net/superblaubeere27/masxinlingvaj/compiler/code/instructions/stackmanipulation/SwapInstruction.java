package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.stackmanipulation;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class SwapInstruction extends Instruction {
    private final StackSlot operandA;
    private final StackSlot operandB;

    public SwapInstruction(StackSlot operandA, StackSlot operandB) {
        this.operandA = operandA;
        this.operandB = operandB;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var a = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.operandA);
        var b = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.operandB);

        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                this.operandA,
                b
        );
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                this.operandB,
                a
        );
    }
}
