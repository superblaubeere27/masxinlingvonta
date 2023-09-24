package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstBoolExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstIntExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstLongExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstNullExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionPredicates;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.Optional;

public class ExpressionSimplifier {
    private final CompilerIndex index;

    public ExpressionSimplifier(CompilerIndex index) {
        this.index = index;
    }

    private static Optional<Long> extractConstIntValue(Expr expr) {
        if (expr instanceof ConstIntExpr constIntExpr) {
            return Optional.of((long) constIntExpr.getValue());
        } else if (expr instanceof ConstLongExpr constLongExpr) {
            return Optional.of(constLongExpr.getValue());
        } else if (expr instanceof ConstBoolExpr constLongExpr) {
            return Optional.of(constLongExpr.getValue() ? 1L : 0);
        }

        return Optional.empty();
    }

    private static ConstNullExpr simplifyVarExpr(LocalInfoSnapshot snapshot, VarExpr varExpr) {
        var localInfo = snapshot.getOrCreateLocalInfo(varExpr.getLocal());

        if (AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(false)) {
            return new ConstNullExpr();
        }

        return null;
    }

    private static ConstBoolExpr simplifyObjectCompare(ObjectCompareExpr expr, LocalInfoSnapshot snapshot) {
        if (expr.getLhs().getOpcode() == Opcode.CONST_NULL && expr.getRhs().getOpcode() == Opcode.CONST_NULL)
            return new ConstBoolExpr(true);

        VarExpr comparedVar;
        Expr otherExpr;

        if (expr.getLhs() instanceof VarExpr) {
            comparedVar = (VarExpr) expr.getLhs();
            otherExpr = expr.getRhs();
        } else if (expr.getRhs() instanceof VarExpr) {
            comparedVar = (VarExpr) expr.getRhs();
            otherExpr = expr.getLhs();
        } else {
            return null;
        }

        if (otherExpr.getOpcode() != Opcode.CONST_NULL)
            return null;

        var localInfo = snapshot.getOrCreateLocalInfo(comparedVar.getLocal());

        var nullState = AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_NULL_STATE_PREDICATE);

        if (nullState.isPresent()) {
            return new ConstBoolExpr(nullState.get());
        }

        return null;
    }

    /**
     * Returns the simplified expression if possible, otherwise it just returns expr
     */
    public Expr simplifyExpressionIfPossible(LocalInfoSnapshot snapshot, Expr expr) {
        Expr simplified = this.simplifyExpression(snapshot, expr);

        return simplified != null ? simplified : expr;
    }

    public Expr simplifyExpression(LocalInfoSnapshot snapshot, Expr expr) {
        if (expr instanceof ObjectCompareExpr objectCompareExpr) {
            return simplifyObjectCompare(objectCompareExpr, snapshot);
        } else if (expr instanceof IntegerCompareExpr integerCompareExpr) {
            return simplifyIntegerCompare(integerCompareExpr);
        } else if (expr instanceof CheckCastExpr checkCastExpr) {
            return simplifyCheckCast((CheckCastExpr) expr, snapshot, checkCastExpr);
        } else if (expr instanceof VarExpr varExpr) {
            return simplifyVarExpr(snapshot, varExpr);
        }

        return null;
    }

    private Expr simplifyIntegerCompare(IntegerCompareExpr expr) {
        var lhsOption = extractConstIntValue(expr.getLhs());
        var rhsOption = extractConstIntValue(expr.getRhs());

        if (rhsOption.isEmpty() || lhsOption.isEmpty()) {
            return null;
        }

        long lhs = lhsOption.get();
        long rhs = rhsOption.get();

        boolean actualValue = switch (expr.getOperator()) {
            case EQUAL -> lhs == rhs;
            case NOT_EQUAL -> lhs != rhs;
            case LOWER -> lhs < rhs;
            case LOWER_EQUAL -> lhs <= rhs;
            case GREATER -> lhs > rhs;
            case GREATER_EQUAL -> lhs >= rhs;
        };

        return new ConstBoolExpr(actualValue);
    }

    private Expr simplifyCheckCast(CheckCastExpr expr, LocalInfoSnapshot snapshot, CheckCastExpr checkCastExpr) {
        if (!(checkCastExpr.getInstance() instanceof VarExpr instance))
            return null;

        var localInfo = snapshot.getOrCreateLocalInfo(instance.getLocal());

        if (localInfo.canBeAssumed(assumption -> AssumptionPredicates.canBeCastedTo(assumption, this.index, checkCastExpr.getCheckedType()))) {
            return expr.getInstance().copy();
        }

        return null;
    }

}
