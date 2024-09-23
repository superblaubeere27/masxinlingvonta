package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import java.util.Objects;
import java.util.function.Function;

public class ParentArrayAssumption extends Assumption {
    private final Assumption assumption;

    public ParentArrayAssumption(Assumption assumption) {
        this.assumption = assumption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParentArrayAssumption that = (ParentArrayAssumption) o;
        return Objects.equals(assumption, that.assumption);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(assumption);
    }

    @Override
    public Assumption remapAssumption(Function<Assumption, Assumption> remapper) {
        var remappedThis = remapper.apply(this);

        if (remappedThis != null) {
            return remappedThis;
        }

        var remappedContent = remapper.apply(this.assumption);

        if (remappedContent != null) {
            return remappedContent == NoAssumption.INSTANCE ? NoAssumption.INSTANCE : new ParentArrayAssumption(remappedContent);
        }

        return this;
    }

    @Override
    public String toString() {
        return "parent-array: (" + this.assumption.toString() + ")";
    }
}
