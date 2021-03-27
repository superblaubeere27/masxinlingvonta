package net.superblaubeere27.masxinlingvaj.compiler.code;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;

public abstract class Instruction {
    /**
     * Does this instruction terminate it's basic block?
     */
    public abstract boolean isTerminating();

    public abstract void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block);
}
