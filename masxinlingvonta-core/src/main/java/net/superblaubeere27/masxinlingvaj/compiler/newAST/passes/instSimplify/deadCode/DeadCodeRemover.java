package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.deadCode;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.LT79Dom;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.SSABlockLivenessAnalyser;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.InstProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ArrayStoreStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.DeleteRefStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class DeadCodeRemover extends Pass {

    public static List<Expr> extractNonRedundantExpressions(Stmt stmt) {
        if (stmt instanceof CopyPhiStmt) {
            return Collections.emptyList();
        }

        ArrayList<Expr> expressions = new ArrayList<>();

        for (Expr child : stmt.getChildren()) {
            extractNonRedundantExpressions0(child, expressions);
        }

        return expressions;
    }

    private static void extractNonRedundantExpressions0(Expr expr, List<Expr> exprList) {
        if (hasSideEffects(expr)) {
            exprList.add(expr);
            return;
        }

        for (Expr child : expr.getChildren()) {
            extractNonRedundantExpressions0(child, exprList);
        }
    }

    public static boolean hasSideEffects(Expr expr) {
        return expr.getMetadata().getProperties().stream().anyMatch(InstProperty::changesState);
    }

    private static boolean isArrayDeadAfter(Stmt stmt, Local arrayVar, SSABlockLivenessAnalyser liveness, LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dominanceAnalyzer) {
        // Check if the array is dead after the block
        if (liveness.out(stmt.getBlock()).contains(arrayVar)) {
            return false;
        }
        var stmtIndex = stmt.getBlock().indexOf(stmt);

        var varUses = stmt.getBlock().getGraph().getLocals().uses.get(arrayVar);
        var stmtBlockDominates = dominanceAnalyzer.getDominates(stmt.getBlock());

        return varUses.stream().noneMatch(x -> {
            // If the usage is before the statement, it does not count as usage.
            if (x.getBlock().equals(stmt.getBlock()) && x.getBlock().indexOf(x) < stmtIndex) {
                return false;
            }
            // If the usage is not dominated by the statement block, it does not count as usage.
            if (!stmtBlockDominates.contains(x.getBlock())) {
                return false;
            }

            return !(x.getParent() instanceof ArrayLengthExpr);
        });
    }

    private static void findReferencedLocals(ControlFlowGraph cfg, HashSet<Local> referencedLocals) {
        referencedLocals.clear();

        for (BasicBlock block : cfg.vertices()) {
            for (Stmt stmt : block) {
                Local declaredVar = null;

                if (stmt instanceof AbstractCopyStmt) {
                    declaredVar = ((AbstractCopyStmt) stmt).getVariable().getLocal();
                }

                for (Expr child : stmt.enumerateOnlyChildren()) {
                    if (!(child instanceof VarExpr)) {
                        continue;
                    }

                    var usedVar = ((VarExpr) child).getLocal();

                    if (usedVar.equals(declaredVar))
                        continue;

                    referencedLocals.add(usedVar);
                }
            }
        }

    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        var transaction = new StatementTransaction();
        var referencedLocals = new HashSet<Local>();

        boolean change;

        do {
            change = runSimpleLoop(cfg, referencedLocals, transaction);
            change |= runComplexLoop(cfg, transaction);
        } while (change);
    }

    private boolean runComplexLoop(ControlFlowGraph cfg, StatementTransaction transaction) {
        var dominanceAnalyzer = new LT79Dom<>(cfg, cfg.getEntry());
        int n = 0;

        do {
            var lifenessAnalyzer = new SSABlockLivenessAnalyser(cfg);

            removeDeadCode(cfg, transaction, lifenessAnalyzer, dominanceAnalyzer);

            n++;
        } while (transaction.apply());

        return n > 1;
    }

    private void removeDeadCode(ControlFlowGraph cfg, StatementTransaction transaction, SSABlockLivenessAnalyser liveness, LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dominanceAnalyzer) {
        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                if (!(stmt instanceof ArrayStoreStmt arrayStoreStmt && arrayStoreStmt.getArray() instanceof VarExpr arrayVar)) {
                    continue;
                }

                // TODO: This code is dysfunctional since it does not feature a working escape analysis.
//                if (isArrayDeadAfter(stmt, arrayVar.getLocal(), liveness, dominanceAnalyzer)) {
//                    transaction.removeStatementAndExtractSideEffects(stmt);
//                }
            }
        }
    }

    private boolean runSimpleLoop(ControlFlowGraph cfg, HashSet<Local> referencedLocals, StatementTransaction transaction) {
        int n = 0;

        do {
            findReferencedLocals(cfg, referencedLocals);

            removeDeadAssignments(cfg, transaction, referencedLocals);

            n++;
        } while (transaction.apply());

        return n > 1;
    }

    private void removeDeadAssignments(ControlFlowGraph cfg, StatementTransaction transaction, HashSet<Local> referencedLocals) {
        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                if (isRedundant(stmt)) {
                    transaction.removeStatementAndExtractSideEffects(stmt);

                    continue;
                }

                if (!(stmt instanceof AbstractCopyStmt))
                    continue;

                if (referencedLocals.contains(((AbstractCopyStmt) stmt).getVariable().getLocal())) {
                    continue;
                }

                transaction.removeStatementAndExtractSideEffects(stmt);
            }
        }
    }

    private boolean isRedundant(Stmt stmt) {
        return stmt instanceof DeleteRefStmt deleteRefStmt && deleteRefStmt.getObject().getOpcode() == Opcode.CONST_NULL;
    }

}
