package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class Assumption {

    public Assumption merge(Assumption other) {
        return LinkedAssumptions.or(this, other);
    }

    public abstract boolean equivalent(Assumption other);

    // TODO is this method used? yes, is it?

    /**
     * This assumption is x and there is an assumption linkage like this: x OR y.
     * Now a new assumption (z) will be added with an AND linkage: (x OR y) AND z.
     * This method now remaps assumption x to make sense in the context of the z-assumption.
     * <p/>
     * If x is the same or contradicts z, this function returns Optional(NoAssumption).
     * If x can be simplified due to z, this function returns Optional(new assumption)
     * If x and z mean different things, this function will return Optional.empty().
     */
    public Optional<Assumption> remapAssumptionForAnd(Assumption other) {
        if (other.equivalent(this))
            return Optional.of(NoAssumption.INSTANCE);

        return Optional.empty();
    }

    public final boolean canBeAssumed(Predicate<Assumption> leafPredicate) {
        return AssumptionAnalyzer.canBeAssumed(this, leafPredicate);
    }

    public final <T> Optional<T> extractValue(Function<Assumption, Optional<T>> leafPredicate) {
        return AssumptionAnalyzer.extractValue(this, leafPredicate);
    }

    @Override
    public abstract String toString();

    public static class NoAssumption extends Assumption {
        public static final NoAssumption INSTANCE = new NoAssumption();

        private NoAssumption() {
        }

        @Override
        public boolean equivalent(Assumption other) {
            return other == INSTANCE;
        }

        @Override
        public Optional<Assumption> remapAssumptionForAnd(Assumption other) {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "???";
        }
    }
}
