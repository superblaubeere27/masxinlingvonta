package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

/**
 * Assumption about whether the object is null or not.
 */
public class NullStateAssumption extends Assumption {
    public static final NullStateAssumption IS_NULL = new NullStateAssumption(true);
    public static final NullStateAssumption IS_NON_NULL = new NullStateAssumption(false);

    private final boolean isNull;

    private NullStateAssumption(boolean isNull) {
        this.isNull = isNull;
    }

    public static NullStateAssumption of(boolean isNull) {
        return isNull ? IS_NULL : IS_NON_NULL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NullStateAssumption that = (NullStateAssumption) o;
        return isNull == that.isNull;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isNull);
    }

    @Override
    public String toString() {
        return this.isNull ? "is null" : "is non-null";
    }

    public boolean isNull() {
        return isNull;
    }
}
