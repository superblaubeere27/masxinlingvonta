package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.InstProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.DeleteRefStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class RedundantExpressionAndAssignmentRemover extends Pass {

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

    @Override
    public void apply(ControlFlowGraph cfg) {
        HashSet<Local> referencedLocals = new HashSet<>();

        var transaction = new StatementTransaction();

        do {
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

        } while (transaction.apply());
    }

    private boolean isRedundant(Stmt stmt) {
        return stmt instanceof DeleteRefStmt deleteRefStmt && deleteRefStmt.getObject().getOpcode() == Opcode.CONST_NULL;
    }

}
