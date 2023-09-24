package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.PrimitiveAssumptionState;

public class CallGraphState {
    /**
     * Is known whether an exception was thrown?
     */
    private PrimitiveAssumptionState<Boolean> exceptionState;

    private CallGraphState(PrimitiveAssumptionState<Boolean> exceptionState) {
        this.exceptionState = exceptionState;
    }

    public static CallGraphState create() {
        return new CallGraphState(PrimitiveAssumptionState.assumeUnknown());
    }

    public PrimitiveAssumptionState<Boolean> getExceptionState() {
        return exceptionState;
    }

    public void setExceptionState(PrimitiveAssumptionState<Boolean> exceptionState) {
        this.exceptionState = exceptionState;
    }

    public CallGraphState merge(CallGraphState other) {
        return new CallGraphState(this.exceptionState.merge(other.exceptionState));
    }

    public CallGraphState copy() {
        return new CallGraphState(exceptionState);
    }

    @Override
    public String toString() {
        return "CallGraphState{" +
                "exceptionState=" + exceptionState +
                '}';
    }

    public boolean equivalent(CallGraphState callGraphState) {
        return this.exceptionState.equivalent(callGraphState.exceptionState);
    }
}
