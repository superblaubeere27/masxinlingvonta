package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class Assumption {

    public Assumption merge(Assumption other) {
        return LinkedAssumptions.or(this, other);
    }

    public final boolean canBeAssumed(Predicate<Assumption> leafPredicate) {
        return AssumptionAnalyzer.canBeAssumed(this, leafPredicate);
    }

    public final <T> Optional<T> extractValue(Function<Assumption, Optional<T>> leafPredicate) {
        return AssumptionAnalyzer.extractValue(this, leafPredicate);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    /**
     * Remaps an assumption.
     *
     * @param remapper returns a remapped assumption. If the assumption remains unchanged, this function should return
     *                 null.
     * @return null if the assumption remains unchanged.
     */
    public Assumption remapAssumption(Function<Assumption, Assumption> remapper) {
        return remapper.apply(this);
    }

    /**
     * Keys to group the assumptions by. If another assumption has a different key, both assumptions are completely
     * unrelated.
     */
    public AssumptionKey getKey() {
        return new ClassAssumptionKey(this.getClass());
    }

    public static class NoAssumption extends Assumption {
        public static final NoAssumption INSTANCE = new NoAssumption();

        private NoAssumption() {
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            return obj == INSTANCE;
        }

        @Override
        public int hashCode() {
            return NoAssumption.class.hashCode();
        }

        @Override
        public String toString() {
            return "???";
        }
    }

    public abstract static class AssumptionKey {
        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(Object obj);
    }

    /**
     * Default implementation
     */
    protected static final class ClassAssumptionKey extends AssumptionKey {
        private final Class<? extends Assumption> parentClass;

        public ClassAssumptionKey(Class<? extends Assumption> parentClass) {
            this.parentClass = parentClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassAssumptionKey that = (ClassAssumptionKey) o;
            return Objects.equals(parentClass, that.parentClass);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(parentClass);
        }
    }

    /**
     * Default implementation
     */
    protected static final class SingleKeyAssumptionKey extends AssumptionKey {
        private final Class<? extends Assumption> parentClass;
        private final Object key;

        public SingleKeyAssumptionKey(Class<? extends Assumption> parentClass, Object key) {
            this.parentClass = parentClass;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SingleKeyAssumptionKey that = (SingleKeyAssumptionKey) o;
            return Objects.equals(parentClass, that.parentClass) && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentClass, key);
        }
    }
}
