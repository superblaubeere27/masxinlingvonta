package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

import java.util.HashMap;
import java.util.Map;

public class LocalInfoSnapshot {
    private final HashMap<Local, LocalInfo> localInfos;
    private final CallGraphState callGraphState;

    private LocalInfoSnapshot(HashMap<Local, LocalInfo> localInfos, CallGraphState callGraphState) {
        this.localInfos = localInfos;
        this.callGraphState = callGraphState;
    }

    public static LocalInfoSnapshot create() {
        return new LocalInfoSnapshot(new HashMap<>(), CallGraphState.create());
    }

    public HashMap<Local, LocalInfo> getLocalInfos() {
        return localInfos;
    }

    public LocalInfoSnapshot merge(LocalInfoSnapshot other) {
        var newLocalInfos = new HashMap<>(this.localInfos);

        for (Map.Entry<Local, LocalInfo> localLocalInfoEntry : other.localInfos.entrySet()) {
            var currentLocalInfo = newLocalInfos.get(localLocalInfoEntry.getKey());

            if (currentLocalInfo != null) {
                newLocalInfos.put(localLocalInfoEntry.getKey(), currentLocalInfo.merge(localLocalInfoEntry.getValue()));
            } else {
                newLocalInfos.put(localLocalInfoEntry.getKey(), localLocalInfoEntry.getValue());
            }
        }

        return new LocalInfoSnapshot(newLocalInfos, callGraphState.merge(other.callGraphState));
    }

    public boolean isEquivalent(LocalInfoSnapshot other) {
        for (Map.Entry<Local, LocalInfo> local : this.localInfos.entrySet()) {
            var a = other.localInfos.get(local.getKey());
            var b = local.getValue();

            if (a == null && b != null || b == null && a != null || a != null && !a.equivalent(b)) {
                return false;
            }
        }

        return true;
    }

    public CallGraphState getCallGraphState() {
        return callGraphState;
    }

    public LocalInfoSnapshot copy() {
        return new LocalInfoSnapshot(new HashMap<>(this.localInfos), callGraphState.copy());
    }

    public void putLocalInfo(Local local, LocalInfo localInfo) {
        this.localInfos.put(local, localInfo);
    }

    public ObjectLocalInfo getOrCreateObjectLocalInfo(Local local) {
        return (ObjectLocalInfo) getLocalInfos().computeIfAbsent(local, t -> ObjectLocalInfo.create());
    }

    @Override
    public String toString() {
        return "LocalInfoSnapshot{" +
                "localInfos=" + localInfos +
                ", callGraphState=" + callGraphState +
                '}';
    }
}
