package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.conditionalAssumptions;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.NumberRelation;

import java.util.Objects;
import java.util.function.Function;

public class ConditionalAssumptionByNumberValue<N extends Number> extends Assumption {
    private final NumberRelation<N> relation;
    private final Local targetLocal;
    private final Assumption thenAssumption;

    public ConditionalAssumptionByNumberValue(NumberRelation<N> relation, Local targetLocal, Assumption thenAssumption) {
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
    public Assumption remapAssumption(Function<Assumption, Assumption> remapper) {
        var remappedThis = remapper.apply(this);

        if (remappedThis != null) {
            return remappedThis;
        }

        var remappedContent = remapper.apply(this.thenAssumption);

        if (remappedContent != null) {
            return remappedContent == NoAssumption.INSTANCE ? NoAssumption.INSTANCE : new ConditionalAssumptionByNumberValue<>(this.relation, this.targetLocal, this.thenAssumption);
        }

        return this;

    }

    @Override
    public AssumptionKey getKey() {
        return new SingleKeyAssumptionKey(this.getClass(), this.targetLocal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalAssumptionByNumberValue<?> that = (ConditionalAssumptionByNumberValue<?>) o;
        return Objects.equals(relation, that.relation) && Objects.equals(targetLocal, that.targetLocal) && Objects.equals(thenAssumption, that.thenAssumption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relation, targetLocal, thenAssumption);
    }

    @Override
    public String toString() {
        return "when " + relation.toString() + " then " + thenAssumption.toString() + " for " + targetLocal;
    }
}
