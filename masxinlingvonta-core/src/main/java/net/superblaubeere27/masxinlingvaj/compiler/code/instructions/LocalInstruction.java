package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;

public class LocalInstruction extends Instruction {
    private final StackSlot slot;
    private final int idx;
    private final boolean store;

    public LocalInstruction(StackSlot slot, int idx, boolean store) {
        this.slot = slot;
        this.idx = idx;
        this.store = store;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var affectedLocalSlot = new StackSlot(this.slot.getType(), this.idx);

        if (store) {
            translatedMethod.getStack().buildLocalStore(
                    builder,
                    affectedLocalSlot,
                    translatedMethod.getStack().buildStackLoad(builder, this.slot)
            );
        } else {
            translatedMethod.getStack().buildStackStore(
                    builder,
                    this.slot,
                    translatedMethod.getStack().buildLocalLoad(builder, affectedLocalSlot)
            );
        }
    }
}
