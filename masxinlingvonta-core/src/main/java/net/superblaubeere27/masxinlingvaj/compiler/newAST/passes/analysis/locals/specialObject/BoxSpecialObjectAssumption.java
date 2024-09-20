package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.specialObject;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

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
    public boolean equivalent(Assumption other) {
        return other instanceof BoxSpecialObjectAssumption otherBox && this.boxType.equals(otherBox.boxType) && this.assumption.equivalent(otherBox);
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
