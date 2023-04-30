package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ExprProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.*;
import java.util.stream.Collectors;

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
        if (expr.getMetadata().getProperties().stream().anyMatch(ExprProperty::changesState)) {
            exprList.add(expr);
            return;
        }

        for (Expr child : expr.getChildren()) {
            extractNonRedundantExpressions0(child, exprList);
        }
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
                    if (!(stmt instanceof AbstractCopyStmt))
                        continue;

                    if (referencedLocals.contains(((AbstractCopyStmt) stmt).getVariable().getLocal())) {
                        continue;
                    }

                    transaction.replaceStatement(stmt, extractNonRedundantExpressions(stmt).stream().map(x -> new ExpressionStmt(x.copy())).collect(Collectors.toList()));
                }
            }

        } while (transaction.apply());
    }

}
