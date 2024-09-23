package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.specialObject;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

public class ArrayLengthAssumption extends Assumption {
    private final Assumption arrayLength;

    public ArrayLengthAssumption(Assumption arrayLength) {
        this.arrayLength = arrayLength;
    }

    public Assumption getArrayLength() {
        return arrayLength;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayLengthAssumption that = (ArrayLengthAssumption) o;
        return Objects.equals(arrayLength, that.arrayLength);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(arrayLength);
    }

    @Override
    public String toString() {
        return "arraylen: " + this.arrayLength.toString();
    }
}
