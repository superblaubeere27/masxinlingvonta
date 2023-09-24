package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LocalInfoSnapshot {
    private final HashMap<Local, Assumption> localInfos;
    private final CallGraphState callGraphState;

    private LocalInfoSnapshot(HashMap<Local, Assumption> localInfos, CallGraphState callGraphState) {
        this.localInfos = localInfos;
        this.callGraphState = callGraphState;
    }

    public static LocalInfoSnapshot create() {
        return new LocalInfoSnapshot(new HashMap<>(), CallGraphState.create());
    }

    public HashMap<Local, Assumption> getLocalInfos() {
        return localInfos;
    }

    public LocalInfoSnapshot merge(LocalInfoSnapshot other) {
        var newLocalInfos = new HashMap<>(this.localInfos);

        for (Map.Entry<Local, Assumption> localLocalInfoEntry : other.localInfos.entrySet()) {
            var currentLocalInfo = this.getLocalInfo(localLocalInfoEntry.getKey());

            newLocalInfos.put(localLocalInfoEntry.getKey(), currentLocalInfo.merge(Objects.requireNonNullElse(localLocalInfoEntry.getValue(), Assumption.NoAssumption.INSTANCE)));
        }

        return new LocalInfoSnapshot(newLocalInfos, callGraphState.merge(other.callGraphState));
    }

    public boolean isEquivalent(LocalInfoSnapshot other) {
        for (Map.Entry<Local, Assumption> local : this.localInfos.entrySet()) {
            var a = other.getLocalInfo(local.getKey());
            var b = Objects.requireNonNullElse(local.getValue(), Assumption.NoAssumption.INSTANCE);

            if (!a.equivalent(b)) {
                return false;
            }
        }

        return this.callGraphState.equivalent(other.callGraphState);
    }

    public CallGraphState getCallGraphState() {
        return callGraphState;
    }

    public LocalInfoSnapshot copy() {
        return new LocalInfoSnapshot(new HashMap<>(this.localInfos), callGraphState.copy());
    }

    public void putLocalInfo(Local local, Assumption localInfo) {
        this.localInfos.put(local, localInfo);
    }

    public Assumption getOrCreateLocalInfo(Local local) {
        return getLocalInfos().computeIfAbsent(local, t -> Assumption.NoAssumption.INSTANCE);
    }

    @Nonnull
    public Assumption getLocalInfo(Local local) {
        return Objects.requireNonNullElse(getLocalInfos().get(local), Assumption.NoAssumption.INSTANCE);
    }

    @Override
    public String toString() {
        return "LocalInfoSnapshot{" +
                "localInfos=" + localInfos +
                ", callGraphState=" + callGraphState +
                '}';
    }
}
