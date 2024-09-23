package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.LT79Dom;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.StmtMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

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

    public StmtMetadata getMetadata() {
        return new StmtMetadata(Collections.emptySet());
    }

    public Iterable<CodeUnit> enumerateWithSelf() {
//		Set<CodeUnit> set = new HashSet<>(_enumerate());
        @SuppressWarnings("unchecked")
        Set<CodeUnit> set = (Set<CodeUnit>) (Set<?>) _enumerate();
        set.add(this);
        return set;
    }

    /**
     * Checks if <code>dominated</code> is dominated by <code>possibleDominator</code>. If this instance is dominated,
     * this method returns false.
     */
    public boolean dominates(LT79Dom<BasicBlock, FlowEdge<BasicBlock>> domTree, Stmt dominated) {
        var dominatorBlock = getBlock();

        if (!domTree.getDominates(dominatorBlock).contains(dominated.getBlock()))
            return false;

        if (dominatorBlock == dominated.getBlock())
            return dominatorBlock.indexOf(dominated) > dominatorBlock.indexOf(this);

        return true;
    }

    public void remapChildren(StatementTransaction transaction, Function<Expr, Expr> remapper) {
        for (Expr child : getChildren()) {
            simplifyAndReplaceExpressionRecursively(transaction, child, remapper);
        }
    }

    private void simplifyAndReplaceExpressionRecursively(StatementTransaction transaction, Expr expr, Function<Expr, Expr> remapper) {
        var replacement = remapper.apply(expr);

        // If a replacement was found, replace the expression and stop traversing
        if (replacement != null) {
            transaction.replaceExpr(expr, replacement);

            return;
        }

        for (Expr child : expr.getChildren()) {
            simplifyAndReplaceExpressionRecursively(transaction, child, remapper);
        }
    }
}