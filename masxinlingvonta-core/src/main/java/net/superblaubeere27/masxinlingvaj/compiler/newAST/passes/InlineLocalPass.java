package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.HashMap;
import java.util.Map;

public class InlineLocalPass extends Pass {

    private static boolean inlineLocals(CodeUnit parent, HashMap<Local, Expr> replacementMap) {
        boolean changed = false;

        if (parent instanceof PhiExpr) {
            for (Map.Entry<BasicBlock, Expr> argument : ((PhiExpr) parent).getArguments().entrySet()) {
                var child = argument.getValue();

                if (child instanceof VarExpr) {
                    var replacement = replacementMap.get(((VarExpr) child).getLocal());

                    if (replacement != null) {
                        ((PhiExpr) parent).setArgument(argument.getKey(), replacement.copy());

                        changed = true;
                    }
                }
            }
        }

        for (Expr child : parent.getChildren()) {
            if (child instanceof VarExpr) {
                var replacement = replacementMap.get(((VarExpr) child).getLocal());

                if (replacement != null) {
                    parent.getBlock().getGraph().writeAt(parent, child, replacement.copy());

                    changed = true;
                }

                continue;
            }

            changed |= inlineLocals(child, replacementMap);
        }

        return changed;
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        HashMap<Local, Expr> localValueReplacements = new HashMap<>();

        boolean changed;

        do {
            localValueReplacements.clear();
            changed = simplifyPhis(cfg);

            for (BasicBlock block : cfg.vertices()) {
                for (Stmt stmt : block) {
                    if (!(stmt instanceof CopyVarStmt varStmt)) {
                        continue;
                    }

                    Local local = varStmt.getVariable().getLocal();

                    if (!local.isSSA())
                        continue;

                    if (varStmt.getExpression().getMetadata().getExprClass() != ExprMetadata.ExprClass.FIRST)
                        continue;

                    if (varStmt.getExpression() instanceof VarExpr && ((VarExpr) varStmt.getExpression()).getLocal().equals(local))
                        continue;

                    if (localValueReplacements.put(local, varStmt.getExpression()) != null)
                        throw new IllegalStateException("wtf? This shouldn't happen in SSA-form");
                }
            }

            for (BasicBlock vertex : cfg.vertices()) {
                for (Stmt stmt : vertex) {
                    changed |= inlineLocals(stmt, localValueReplacements);
                }
            }
        } while (changed);
    }

    public static boolean simplifyPhis(ControlFlowGraph cfg) {
        var transaction = new StatementTransaction();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                if (stmt instanceof CopyPhiStmt) {
                    PhiExpr expression = ((CopyPhiStmt) stmt).getExpression();

                    Expr replacement = null;

                    if (expression.getArgumentCount() == 1) {
                        replacement = expression.getArguments().values().iterator().next().copy();
                    } else if (expression.getArgumentCount() >= 1) {
                        var first = expression.getArguments().values().iterator().next();

                        if (!expression.getArguments().values().stream().anyMatch(x -> !x.equivalent(first))) {
                            replacement = first.copy();
                        }
                    }

                    if (replacement != null)
                        transaction.replaceStatement(stmt, new CopyVarStmt(((CopyPhiStmt) stmt).getVariable(), replacement));
                }
            }
        }

        return transaction.apply();
    }

}
