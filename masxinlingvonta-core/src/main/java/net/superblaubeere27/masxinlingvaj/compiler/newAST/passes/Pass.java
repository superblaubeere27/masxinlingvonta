package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;

public abstract class Pass {
    public abstract void apply(ControlFlowGraph cfg);
}
