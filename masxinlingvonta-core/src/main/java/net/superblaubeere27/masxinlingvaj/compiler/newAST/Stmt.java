package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;

import java.util.Set;

public abstract class Stmt extends CodeUnit {

    public Stmt(int opcode) {
        super(opcode);

        flags |= FLAG_STMT;
    }

    @Override
    public abstract Stmt copy();

    public abstract void compile(ImmToLLVMIRCompiler ctx);

    @Override
    public void setBlock(BasicBlock block) {
        if (block == null) {
            onRemoval(this.getBlock());
        } else {
            if (this.getBlock() != null)
                onRemoval(this.getBlock());

            onAddition(block);
        }

        super.setBlock(block);
    }

    /**
     * Is called when a statement is added to a basic block to add edges, validate, etc.
     */
    public void onAddition(BasicBlock basicBlock) {
        for (Expr e : this.enumerateOnlyChildren()) {
            if (e.getOpcode() == Opcode.LOCAL_LOAD) {
                VarExpr v = (VarExpr) e;

                basicBlock.cfg.getLocals().uses.getNonNull(v.getLocal()).add(v);
            }
        }
    }

    /**
     * Called when a statement is removed from a basic block
     *
     * @param basicBlock the basic block the statement is removed from
     */
    public void onRemoval(BasicBlock basicBlock) {
        for (Expr e : this.enumerateOnlyChildren()) {
            if (e.getOpcode() == Opcode.LOCAL_LOAD) {
                VarExpr v = (VarExpr) e;

                basicBlock.cfg.getLocals().uses.getNonNull(v.getLocal()).remove(v);
            }
        }
    }

    public Iterable<CodeUnit> enumerateWithSelf() {
//		Set<CodeUnit> set = new HashSet<>(_enumerate());
        @SuppressWarnings("unchecked")
        Set<CodeUnit> set = (Set<CodeUnit>) (Set<?>) _enumerate();
        set.add(this);
        return set;
    }
}