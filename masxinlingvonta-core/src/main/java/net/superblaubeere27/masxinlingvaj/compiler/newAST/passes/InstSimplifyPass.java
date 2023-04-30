package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstBoolExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstNullExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectLocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.ArrayList;
import java.util.Arrays;

public class InstSimplifyPass extends Pass {
    private final CompilerIndex index;

    public InstSimplifyPass(CompilerIndex index) {
        this.index = index;
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
        var replacement = simplifyExpression(analyzer, expr);

        // If a replacement was found, replace the expression and stop traversing
        if (replacement != null) {
            transaction.replaceExpr(expr, replacement);

            return;
        }

        for (Expr child : expr.getChildren()) {
            simplifyAndReplaceExpressionRecursively(transaction, analyzer, child);
        }
    }

    private Expr simplifyExpression(LocalVariableAnalyzer analyzer, Expr expr) {
        var snapshot = analyzer.getStatementSnapshot(expr.getRootParent());

        if (expr instanceof ObjectCompareExpr) {
            var compareExpr = (ObjectCompareExpr) expr;

            if (compareExpr.getLhs().getOpcode() == Opcode.CONST_NULL && compareExpr.getRhs().getOpcode() == Opcode.CONST_NULL)
                return new ConstBoolExpr(true);

            VarExpr comparedVar;
            Expr otherExpr;

            if (compareExpr.getLhs() instanceof VarExpr) {
                comparedVar = (VarExpr) compareExpr.getLhs();
                otherExpr = compareExpr.getRhs();
            } else if (compareExpr.getRhs() instanceof VarExpr) {
                comparedVar = (VarExpr) compareExpr.getRhs();
                otherExpr = compareExpr.getLhs();
            } else {
                return null;
            }

            var localInfo = snapshot.getOrCreateObjectLocalInfo(comparedVar.getLocal());

            if (otherExpr instanceof ConstNullExpr) {
                var nullAssumptionState = localInfo.getIsNullAssumption();

                if (!nullAssumptionState.isUnknown()) {
                    return new ConstBoolExpr(nullAssumptionState.getAssumedValue());
                }
            }
        } else if (expr instanceof CheckCastExpr checkCastExpr) {
            if (!(checkCastExpr.getInstance() instanceof VarExpr instance))
                return null;

            ObjectLocalInfo objectLocalInfo = snapshot.getOrCreateObjectLocalInfo(instance.getLocal());

            var checkedClass = this.index.getClass(checkCastExpr.getCheckedType());

            var isCastRedundant = Arrays.stream(objectLocalInfo.getObjectTypeAssumption().getKnownInfos())
                    .anyMatch(
                            x -> {
                                var targetClass = this.index.getClass(x.type().getTypeOfObjectOrArray());

                                return x.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF || x.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_EXACTLY
                                        && !x.inverted()
                                        && (ClassHierarchyBuilder.isInstanceOf(targetClass, checkedClass)
                                        || x.type().getTypeOfObjectOrArray().equals(checkCastExpr.getCheckedType()));
                            }
                    );

            if (isCastRedundant) {
                return ((CheckCastExpr) expr).getInstance().copy();
            }
        }

        return null;
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

                    BranchSimplifier.trySimplifyBranch(analyzer, transaction, (BranchStmt) stmt);

                    transaction.apply();
                }
            }
        }
    }

}
