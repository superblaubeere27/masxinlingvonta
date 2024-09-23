package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.specialObject;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

/**
 * Assumption about the content of a box (i.e. {@link Integer})
 */
public class BoxSpecialObjectAssumption extends Assumption {
    /**
     * Type of the box. i.e. `java/lang/Integer`
     */
    private final String boxType;
    private final Assumption assumption;

    public BoxSpecialObjectAssumption(String boxType, Assumption assumption) {
        this.boxType = boxType;
        this.assumption = assumption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoxSpecialObjectAssumption that = (BoxSpecialObjectAssumption) o;
        return Objects.equals(boxType, that.boxType) && Objects.equals(assumption, that.assumption);
    }

    @Override
    public AssumptionKey getKey() {
        return new SingleKeyAssumptionKey(this.getClass(), this.boxType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boxType, assumption);
    }

    public String getBoxType() {
        return boxType;
    }

    public Assumption getAssumption() {
        return assumption;
    }

    @Override
    public String toString() {
        return "BoxedContent(" + this.boxType + "): " + this.assumption.toString();
    }
}
