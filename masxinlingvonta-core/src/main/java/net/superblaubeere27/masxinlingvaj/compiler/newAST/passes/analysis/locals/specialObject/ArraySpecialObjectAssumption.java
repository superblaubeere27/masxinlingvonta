package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.specialObject;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

public class ArraySpecialObjectAssumption extends Assumption {
    private final Assumption arrayLength;

    public ArraySpecialObjectAssumption(Assumption arrayLength) {
        this.arrayLength = arrayLength;
    }

    public Assumption getArrayLength() {
        return arrayLength;
    }

    @Override
    public boolean equivalent(Assumption other) {
        return this.arrayLength.equivalent(other);
    }

    @Override
    public String toString() {
        return "arraylen: " + this.arrayLength.toString();
    }
}
