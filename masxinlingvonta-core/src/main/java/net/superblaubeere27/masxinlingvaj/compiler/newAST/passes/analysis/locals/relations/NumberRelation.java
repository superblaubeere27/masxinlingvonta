package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

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
    public boolean equivalent(Assumption other) {
        return other instanceof NumberRelation<?> numberRelation && this.rhs.equals(numberRelation.rhs) && this.operator == numberRelation.operator;
    }

    @Override
    public String toString() {
        return this.operator + " " + this.rhs.toString();
    }
}
