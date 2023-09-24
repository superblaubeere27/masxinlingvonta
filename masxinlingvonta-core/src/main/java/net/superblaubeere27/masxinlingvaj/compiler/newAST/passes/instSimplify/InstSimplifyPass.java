package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.BranchSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.ArrayList;

public class InstSimplifyPass extends Pass {
    private final ExpressionSimplifier simplifier;

    public InstSimplifyPass(CompilerIndex index) {
        this.simplifier = new ExpressionSimplifier(index);
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        simplifyExpressions(cfg);

        cfg.verify();

        simplifyWholeStatements(cfg);

        cfg.verify();
    }

    private void simplifyExpressions(ControlFlowGraph cfg) {
        var transaction = new StatementTransaction();

        LocalVariableAnalyzer analyzer = new LocalVariableAnalyzer(cfg);

        analyzer.analyze();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                for (Expr child : stmt.getChildren()) {
                    simplifyAndReplaceExpressionRecursively(transaction, analyzer, child);
                }
            }
        }

        transaction.apply();
    }

    private void simplifyAndReplaceExpressionRecursively(StatementTransaction transaction, LocalVariableAnalyzer analyzer, Expr expr) {
        var replacement = simplifier.simplifyExpression(analyzer.getStatementSnapshot(expr.getRootParent()), expr);

        // If a replacement was found, replace the expression and stop traversing
        if (replacement != null) {
            transaction.replaceExpr(expr, replacement);

            return;
        }

        for (Expr child : expr.getChildren()) {
            simplifyAndReplaceExpressionRecursively(transaction, analyzer, child);
        }
    }

    private void simplifyWholeStatements(ControlFlowGraph cfg) {
        LocalVariableAnalyzer analyzer = new LocalVariableAnalyzer(cfg);

        analyzer.analyze();

        for (BasicBlock vertex : new ArrayList<>(cfg.vertices())) {
            if (!cfg.containsVertex(vertex))
                continue;

            for (Stmt stmt : vertex) {
                if (stmt instanceof BranchStmt) {
                    var transaction = new StatementTransaction();

                    BranchSimplifier.trySimplifyBranch(this.simplifier, analyzer, transaction, (BranchStmt) stmt);

                    transaction.apply();
                }
            }
        }
    }

}
