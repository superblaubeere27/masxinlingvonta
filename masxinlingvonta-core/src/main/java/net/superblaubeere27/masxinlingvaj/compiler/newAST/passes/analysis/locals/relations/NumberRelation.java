package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

/**
 * Basically says <code>subject operator rhs</code>.
 */
public class NumberRelation<N extends Number> extends Assumption {
    private final RelationObject<N> rhs;
    private final IntegerCompareExpr.Operator operator;

    public NumberRelation(RelationObject<N> rhs, IntegerCompareExpr.Operator operator) {
        this.rhs = rhs;
        this.operator = operator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumberRelation<?> that = (NumberRelation<?>) o;
        return Objects.equals(rhs, that.rhs) && operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rhs, operator);
    }

    public IntegerCompareExpr.Operator getOperator() {
        return operator;
    }

    public RelationObject<N> getRhs() {
        return rhs;
    }

    @Override
    public String toString() {
        return this.operator + " " + this.rhs.toString();
    }
}
