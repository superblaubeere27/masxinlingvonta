package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.reuseLocals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutFieldStmt;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ReusableLocalManager {
    private final HashMap<ReuseCandidateIdentifier, List<ReuseCandidate>> availableCandidates;

    ReusableLocalManager(ControlFlowGraph cfg) {
        this.availableCandidates = collectCandidates(cfg);
    }

    private static HashMap<ReuseCandidateIdentifier, List<ReuseCandidate>> collectCandidates(ControlFlowGraph cfg) {
        var candidates = new HashMap<ReuseCandidateIdentifier, List<ReuseCandidate>>();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                var candidate = getReuseCandidate(stmt);

                if (candidate != null) {
                    candidates.computeIfAbsent(candidate.identifier, (x) -> new ArrayList<>()).add(candidate);
                }
            }
        }

        return candidates;
    }

    private static ReuseCandidate getReuseCandidate(Stmt stmt) {
        if (stmt instanceof CopyVarStmt copyStmt) {
            Expr expression = copyStmt.getExpression();

            var id = getReuseCandidateIdentifierFor(expression);

            // Not every expression is a candidate.
            if (id == null) {
                return null;
            }

            return new ReuseCandidate(copyStmt.getVariable(), copyStmt, id);
        }
        if (stmt instanceof PutFieldStmt putFieldStmt
                && putFieldStmt.getInstance() instanceof VarExpr instanceVarExpr) {
            var id = new InstanceFieldValueReuseCandidateIdentifier(putFieldStmt.getTarget(), instanceVarExpr.getLocal());

            return new ReuseCandidate(putFieldStmt.getValue(), putFieldStmt, id);
        }

        return null;
    }

    private static ReuseCandidateIdentifier getReuseCandidateIdentifierFor(Expr expression) {
        if (expression instanceof GetFieldExpr getFieldExpr
                && getFieldExpr.getInstance() instanceof VarExpr varExpr) {
            return new InstanceFieldValueReuseCandidateIdentifier(getFieldExpr.getTarget(), varExpr.getLocal());
        } else if (expression instanceof GetStaticExpr getFieldExpr) {
            return new StaticFieldValueReuseCandidateIdentifier(getFieldExpr.getTarget());
        } else if (expression instanceof ArrayLengthExpr arrayLengthExpr
                && arrayLengthExpr.getArray() instanceof VarExpr varExpr) {
            return new ArrayLengthReuseCandidateIdentifier(varExpr.getLocal());
        }

        return null;
    }

    public List<ReuseCandidate> getReuseCandidatesFor(Expr expr) {
        return this.availableCandidates.get(getReuseCandidateIdentifierFor(expr));
    }

    interface ReuseCandidateIdentifier {
        boolean allowsMemoryWrite();
    }

    record ReuseCandidate(Expr replacementValue, Stmt declaringStmt, ReuseCandidateIdentifier identifier) {
    }

    record ArrayLengthReuseCandidateIdentifier(Local of) implements ReuseCandidateIdentifier {
        @Override
        public boolean allowsMemoryWrite() {
            return true;
        }
    }

    /**
     * Allows us to reuse the following statement: getfield <i>of</i>.<i>fieldIdentifier</i>.
     */
    record InstanceFieldValueReuseCandidateIdentifier(MethodOrFieldIdentifier fieldIdentifier,
                                                      Local of) implements ReuseCandidateIdentifier {
        @Override
        public boolean allowsMemoryWrite() {
            return false;
        }
    }

    record StaticFieldValueReuseCandidateIdentifier(
            MethodOrFieldIdentifier fieldIdentifier) implements ReuseCandidateIdentifier {
        @Override
        public boolean allowsMemoryWrite() {
            return false;
        }
    }
}
