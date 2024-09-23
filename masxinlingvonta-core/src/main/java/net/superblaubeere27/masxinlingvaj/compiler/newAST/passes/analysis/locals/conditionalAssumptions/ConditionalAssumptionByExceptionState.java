package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.conditionalAssumptions;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

public class ConditionalAssumptionByExceptionState extends Assumption {
    private final boolean exceptionState;
    private final Assumption thenAssumption;

    public ConditionalAssumptionByExceptionState(boolean exceptionState, Assumption thenAssumption) {
        this.exceptionState = exceptionState;
        this.thenAssumption = thenAssumption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalAssumptionByExceptionState that = (ConditionalAssumptionByExceptionState) o;
        return exceptionState == that.exceptionState && Objects.equals(thenAssumption, that.thenAssumption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exceptionState, thenAssumption);
    }

    @Override
    public String toString() {
        return "when ex-state = " + this.exceptionState + ": " + this.thenAssumption;
    }

    public boolean getExceptionState() {
        return exceptionState;
    }

    public Assumption getThenAssumption() {
        return thenAssumption;
    }
}
