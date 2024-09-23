package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LocalInfoSnapshot {
    private final HashMap<Local, Assumption> localAssumptions;
    private final CallGraphState callGraphState;

    private LocalInfoSnapshot(HashMap<Local, Assumption> localAssumptions, CallGraphState callGraphState) {
        this.localAssumptions = localAssumptions;
        this.callGraphState = callGraphState;
    }

    public static LocalInfoSnapshot create() {
        return new LocalInfoSnapshot(new HashMap<>(), CallGraphState.create());
    }

    public HashMap<Local, Assumption> getLocalAssumptions() {
        return localAssumptions;
    }

    public LocalInfoSnapshot merge(List<LocalInfoSnapshot> others) {
        var newLocalInfos = new HashMap<>(this.localAssumptions);
        var newCallGraphState = this.callGraphState;

        for (LocalInfoSnapshot other : others) {
            for (Map.Entry<Local, Assumption> localLocalInfoEntry : other.localAssumptions.entrySet()) {
                var otherAssumption = Objects.requireNonNullElse(localLocalInfoEntry.getValue(), Assumption.NoAssumption.INSTANCE);

                newLocalInfos.compute(localLocalInfoEntry.getKey(), (currentLocal, nullableCurrentAsssumption) -> {
                    var currentAssumption = Objects.requireNonNullElse(nullableCurrentAsssumption, Assumption.NoAssumption.INSTANCE);

                    return currentAssumption.merge(otherAssumption);
                });
            }

            newCallGraphState = callGraphState.merge(other.callGraphState);
        }


        return new LocalInfoSnapshot(newLocalInfos, newCallGraphState);
    }

    public boolean isEquivalent(LocalInfoSnapshot other) {
        for (Map.Entry<Local, Assumption> localAssumptionEntry : this.localAssumptions.entrySet()) {
            if (!Objects.requireNonNullElse(other.localAssumptions.get(localAssumptionEntry.getKey()), Assumption.NoAssumption.INSTANCE).equals(Objects.requireNonNullElse(localAssumptionEntry.getValue(), Assumption.NoAssumption.INSTANCE))) {
                return false;
            }
        }
        for (Map.Entry<Local, Assumption> localAssumptionEntry : other.localAssumptions.entrySet()) {
            if (!Objects.requireNonNullElse(this.localAssumptions.get(localAssumptionEntry.getKey()), Assumption.NoAssumption.INSTANCE).equals(Objects.requireNonNullElse(localAssumptionEntry.getValue(), Assumption.NoAssumption.INSTANCE))) {
                return false;
            }
        }

        return this.callGraphState.equivalent(other.callGraphState);
    }

    public CallGraphState getCallGraphState() {
        return callGraphState;
    }

    public LocalInfoSnapshot copy() {
        return new LocalInfoSnapshot(new HashMap<>(this.localAssumptions), callGraphState.copy());
    }

    public void putLocalAssumption(Local local, Assumption localInfo) {
        Objects.requireNonNull(localInfo);

        this.localAssumptions.put(local, localInfo);
    }

    public Assumption getOrCreateLocalAssumption(Local local) {
        return getLocalAssumptions().computeIfAbsent(local, t -> Assumption.NoAssumption.INSTANCE);
    }

    @Nonnull
    public Assumption getLocalAssumption(Local local) {
        return Objects.requireNonNullElse(getLocalAssumptions().get(local), Assumption.NoAssumption.INSTANCE);
    }

    @Override
    public String toString() {
        return "LocalInfoSnapshot{" +
                "localInfos=" + localAssumptions +
                ", callGraphState=" + callGraphState +
                '}';
    }
}
