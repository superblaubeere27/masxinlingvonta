package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

public class ParentArrayAssumption extends Assumption {
    private final Assumption assumption;

    public ParentArrayAssumption(Assumption assumption) {
        this.assumption = assumption;
    }

    @Override
    public boolean equivalent(Assumption other) {
        return other instanceof ParentArrayAssumption otherParentArrayAssumption && this.assumption.equivalent(otherParentArrayAssumption.assumption);
    }

    @Override
    public String toString() {
        return "parent-array: (" + this.assumption.toString() + ")";
    }
}
