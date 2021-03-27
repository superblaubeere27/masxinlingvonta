package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.objectweb.asm.Type;

public class TypeInstruction extends ConstantInstruction {
    private final Type cst;

    public TypeInstruction(StackSlot outputSlot, Type cst) {
        super(outputSlot);

        this.cst = cst;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        translatedMethod.getStack().buildStackStore(
                translatedMethod.getLlvmBuilder(),
                this.outputSlot,
                translatedMethod.buildFindClass(compiler.getJni(), this.cst.getInternalName())
        );

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }
}
