package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import java.util.Objects;

public final class PrimitiveAssumptionState<T> {
    private static final PrimitiveAssumptionState<Object> UNKNOWN = new PrimitiveAssumptionState<>(true, null);

    private final boolean isUnknown;
    private final T assumedValue;

    private PrimitiveAssumptionState(boolean isUnknown, T assumedValue) {
        this.isUnknown = isUnknown;
        this.assumedValue = assumedValue;
    }

    public static <T> PrimitiveAssumptionState<T> assumeUnknown() {
        return (PrimitiveAssumptionState<T>) UNKNOWN;
    }

    public static <T> PrimitiveAssumptionState<T> assume(T assumption) {
        return new PrimitiveAssumptionState<>(false, assumption);
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    public T getAssumedValue() {
        if (this.isUnknown)
            throw new IllegalStateException("There is no assumed value!");

        return assumedValue;
    }

    public T getAssumedValueNullable() {
        return this.isUnknown ? null : Objects.requireNonNull(assumedValue);
    }

    /**
     * Creates a new assumption value that represents what happens with the current assumption
     * if the value might state change to the given new value.
     * <p>
     * For example...
     * <ul>
     *     <li>...if the old value was <code>true</code> and the possible new value is <code>false</code>, the assumption will switch to an unknown state</li>
     *     <li>...if the old value was <code>true</code> and the possible new value is also <code>true</code> the assumption will stay as-is </li>
     * </ul>
     */
    public PrimitiveAssumptionState<T> mayChangeTo(T possibleNewValue) {
        if (this.isUnknown || !this.assumedValue.equals(possibleNewValue))
            return assumeUnknown();

        return this;
    }

    public PrimitiveAssumptionState<T> merge(PrimitiveAssumptionState<T> other) {
        if (this.isUnknown || other.isUnknown || !other.assumedValue.equals(this.assumedValue))
            return assumeUnknown();

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveAssumptionState<?> that = (PrimitiveAssumptionState<?>) o;
        return isUnknown == that.isUnknown && Objects.equals(assumedValue, that.assumedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isUnknown, assumedValue);
    }

    @Override
    public String toString() {
        return "PrimitiveAssumptionState{" +
                "isUnknown=" + isUnknown +
                ", assumedValue=" + assumedValue +
                '}';
    }

    public boolean equivalent(PrimitiveAssumptionState<T> other) {
        if (this.isUnknown != other.isUnknown)
            return false;

        if (this.isUnknown)
            return true;

        return this.getAssumedValue().equals(other.getAssumedValue());
    }
}
