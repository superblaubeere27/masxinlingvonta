package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.normalize;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

public class NormalizerPass extends Pass {
    public static void normalizeBranch(StatementTransaction transaction, ConditionalBranch branch) {
        if (branch.getCond() instanceof IntegerCompareExpr cmpExpr && cmpExpr.getOperator() == IntegerCompareExpr.Operator.NOT_EQUAL) {
            var lhs = cmpExpr.getLhs().copy();
            var rhs = cmpExpr.getRhs().copy();

            // Invert the cond branch
            transaction.replaceStatement(branch, new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.EQUAL, lhs, rhs), branch.getElseTarget(), branch.getIfTarget()));
        }
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        var transaction = new StatementTransaction();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                if (stmt instanceof ConditionalBranch condBr) {
                    normalizeBranch(transaction, condBr);
                }
            }
        }

        transaction.apply();
    }
}
