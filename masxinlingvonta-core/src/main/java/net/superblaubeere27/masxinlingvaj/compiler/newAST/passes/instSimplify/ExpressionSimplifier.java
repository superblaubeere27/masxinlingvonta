package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.IntegerArithmeticsExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.FloatingPointCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
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

    private static Optional<Long> extractConstIntValue(LocalInfoSnapshot snapshot, Expr expr) {
        if (expr instanceof ConstIntExpr constIntExpr) {
            return Optional.of((long) constIntExpr.getValue());
        } else if (expr instanceof ConstLongExpr constLongExpr) {
            return Optional.of(constLongExpr.getValue());
        } else if (expr instanceof ConstBoolExpr constLongExpr) {
            return Optional.of(constLongExpr.getValue() ? 1L : 0);
        } else if (expr instanceof VarExpr varExpr) {
            var localInfo = snapshot.getOrCreateLocalInfo(varExpr.getLocal());

            return AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_INT_OR_LONG_PREDICATE);
        }

        return Optional.empty();
    }

    private static Optional<Double> extractConstFloatingPointValue(LocalInfoSnapshot snapshot, Expr expr) {
        if (expr instanceof ConstFloatExpr constFloat) {
            return Optional.of((double) constFloat.getValue());
        } else if (expr instanceof ConstDoubleExpr constDouble) {
            return Optional.of(constDouble.getValue());
        } else if (expr instanceof VarExpr varExpr) {
            var localInfo = snapshot.getOrCreateLocalInfo(varExpr.getLocal());

            return AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_FLOAT_OR_DOUBLE_PREDICATE);
        }

        return Optional.empty();
    }

    private static Expr simplifyVarExpr(LocalInfoSnapshot snapshot, VarExpr varExpr) {
        var localInfo = snapshot.getOrCreateLocalInfo(varExpr.getLocal());

        if (AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(false)) {
            return new ConstNullExpr();
        } else if (varExpr.getType() == ImmType.LONG) {
            var assumedValue = AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_INT_OR_LONG_PREDICATE);

            if (assumedValue.isPresent()) {
                return new ConstLongExpr(assumedValue.get());
            }
        } else if (varExpr.getType() == ImmType.INT) {
            var assumedValue = AssumptionAnalyzer.extractValue(localInfo, AssumptionPredicates.GET_INT_OR_LONG_PREDICATE);

            if (assumedValue.isPresent()) {
                return new ConstIntExpr(assumedValue.get().intValue());
            }
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
            var simplifiedBoolExpr = simplifyObjectCompare(objectCompareExpr, snapshot);

            return simplifiedBoolExpr != null ? simplifiedBoolExpr : normalizeObjectCompare(objectCompareExpr);
        } else if (expr instanceof IntegerCompareExpr integerCompareExpr) {
            return simplifyIntegerCompare(snapshot, integerCompareExpr);
        } else if (expr instanceof FloatingPointCompareExpr floatCompareExpr) {
            return simplifyFloatingPointCompare(snapshot, floatCompareExpr);
        } else if (expr instanceof CheckCastExpr checkCastExpr) {
            return simplifyCheckCast(checkCastExpr, snapshot);
        } else if (expr instanceof InstanceOfExpr instanceOfExpr) {
            return simplifyInstanceOf(instanceOfExpr, snapshot);
        } else if (expr instanceof VarExpr varExpr) {
            return simplifyVarExpr(snapshot, varExpr);
        } else if (expr instanceof IntegerArithmeticsExpr integerArithmeticsExpr) {
            return simplifyIntegerArithmetics(integerArithmeticsExpr);
        } else if (expr instanceof ArrayLengthExpr arrayLengthExpr) {
            return simplifyArrayLength(snapshot, arrayLengthExpr);
        }

        return null;
    }

    private Expr simplifyArrayLength(LocalInfoSnapshot snapshot, ArrayLengthExpr arrayLengthExpr) {
        if (!(arrayLengthExpr.getArray() instanceof VarExpr arrayVarExpr))
            return null;

        var localInfo = snapshot.getOrCreateLocalInfo(arrayVarExpr.getLocal());

        return null;
    }

    /**
     * Normalizes an object compare: null == a -> a == null
     */
    private Expr normalizeObjectCompare(ObjectCompareExpr expr) {
        if (expr.getLhs() instanceof ConstNullExpr && expr.getRhs() instanceof VarExpr) {
            return new ObjectCompareExpr(expr.getRhs(), expr.getLhs());
        }

        return null;
    }

    private Expr simplifyIntegerArithmetics(IntegerArithmeticsExpr expr) {
        if ((!(expr.getLhs() instanceof ConstIntExpr lhs) || !(expr.getRhs() instanceof ConstIntExpr rhs))) {
            return null;
        }

        return new ConstIntExpr(expr.getOperator().apply(lhs.getValue(), rhs.getValue()));
    }

    private Expr simplifyIntegerCompare(LocalInfoSnapshot snapshot, IntegerCompareExpr expr) {
        var lhsOption = extractConstIntValue(snapshot, expr.getLhs());
        var rhsOption = extractConstIntValue(snapshot, expr.getRhs());

        if (rhsOption.isEmpty() || lhsOption.isEmpty()) {
            return null;
        }

        long lhs = lhsOption.get();
        long rhs = rhsOption.get();

        return new ConstBoolExpr(expr.getOperator().apply(lhs, rhs));
    }

    private Expr simplifyFloatingPointCompare(LocalInfoSnapshot snapshot, FloatingPointCompareExpr expr) {
        var lhsOption = extractConstFloatingPointValue(snapshot, expr.getLhs());
        var rhsOption = extractConstFloatingPointValue(snapshot, expr.getRhs());

        if (rhsOption.isEmpty() || lhsOption.isEmpty()) {
            return null;
        }

        double lhs = lhsOption.get();
        double rhs = rhsOption.get();

        boolean actualValue = switch (expr.getOperator()) {
            case EQUAL -> lhs == rhs;
            case NOT_EQUAL -> lhs != rhs;
            case LOWER -> lhs < rhs;
            case LOWER_EQUAL -> lhs <= rhs;
            case GREATER -> lhs > rhs;
            case GREATER_EQUAL -> lhs >= rhs;
            case UNORDERED -> Double.isNaN(lhs) || Double.isNaN(rhs);
        };

        return new ConstBoolExpr(actualValue);
    }

    private Expr simplifyCheckCast(CheckCastExpr checkCast, LocalInfoSnapshot snapshot) {
        if (!(checkCast.getInstance() instanceof VarExpr instance))
            return null;

        var localInfo = snapshot.getOrCreateLocalInfo(instance.getLocal());

        if (localInfo.canBeAssumed(assumption -> AssumptionPredicates.canBeCastedTo(assumption, this.index, checkCast.getCheckedType()))) {
            return checkCast.getInstance().copy();
        }

        return null;
    }

    private Expr simplifyInstanceOf(InstanceOfExpr instanceOfExpr, LocalInfoSnapshot snapshot) {
        if (!(instanceOfExpr.getInstance() instanceof VarExpr instance))
            return null;

        var localInfo = snapshot.getOrCreateLocalInfo(instance.getLocal());

        // If the value is null, it cannot be an instance of anything.
        if (localInfo.extractValue(AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(false)) {
            return new ConstBoolExpr(false);
        }

        if (localInfo.canBeAssumed(assumption -> AssumptionPredicates.canBeCastedTo(assumption, this.index, instanceOfExpr.getInstanceOfType()))) {
            return new ConstBoolExpr(true);
        }

        return null;
    }

}
