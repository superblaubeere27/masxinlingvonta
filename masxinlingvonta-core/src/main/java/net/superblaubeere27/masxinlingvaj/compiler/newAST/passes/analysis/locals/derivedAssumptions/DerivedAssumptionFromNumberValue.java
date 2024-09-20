package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.derivedAssumptions;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.NumberRelation;

public class DerivedAssumptionFromNumberValue<N extends Number> extends Assumption {
    private final NumberRelation<N> relation;
    private final Local targetLocal;
    private final Assumption thenAssumption;

    public DerivedAssumptionFromNumberValue(NumberRelation<N> relation, Local targetLocal, Assumption thenAssumption) {
        this.relation = relation;
        this.targetLocal = targetLocal;
        this.thenAssumption = thenAssumption;
    }

    public NumberRelation<N> getRelation() {
        return relation;
    }

    public Assumption getThenAssumption() {
        return thenAssumption;
    }

    public Local getTargetLocal() {
        return targetLocal;
    }

    @Override
    public boolean equivalent(Assumption other) {
        return other instanceof DerivedAssumptionFromNumberValue<?> derivedAssumptionFromNumberValue &&
                this.relation.equivalent(derivedAssumptionFromNumberValue.relation) &&
                this.targetLocal.equals(derivedAssumptionFromNumberValue.targetLocal) &&
                this.thenAssumption.equivalent(derivedAssumptionFromNumberValue.thenAssumption);
    }

    @Override
    public String toString() {
        return "when " + relation.toString() + " then " + thenAssumption.toString() + " for " + targetLocal;
    }
}
