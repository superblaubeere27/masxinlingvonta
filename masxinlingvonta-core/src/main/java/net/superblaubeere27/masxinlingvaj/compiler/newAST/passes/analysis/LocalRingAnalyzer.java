package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;

import java.util.HashMap;
import java.util.HashSet;

public final class LocalRingAnalyzer {
    private final HashSet<LocalVariableRing> rings;
    private final HashMap<Local, LocalVariableRing> ringMap;

    private LocalRingAnalyzer(HashSet<LocalVariableRing> rings, HashMap<Local, LocalVariableRing> ringMap) {
        this.rings = rings;
        this.ringMap = ringMap;
    }

    public static LocalRingAnalyzer buildLocalRing(ControlFlowGraph cfg) {
        var rings = new HashSet<LocalVariableRing>();
        var ringMap = new HashMap<Local, LocalVariableRing>();

        for (Local local : cfg.getLocals().defs.keySet()) {
            var ring = new LocalVariableRing(new HashSet<>());

            buildRing(cfg, local, ring);

            rings.add(ring);

            for (Local variable : ring.variables) {
                ringMap.put(variable, ring);
            }
        }

        return new LocalRingAnalyzer(rings, ringMap);
    }

    private static void buildRing(ControlFlowGraph cfg, Local local, LocalVariableRing currentRing) {
        if (!currentRing.variables.add(local))
            return;

        for (VarExpr varExpr : cfg.getLocals().uses.get(local)) {
            var rootParent = varExpr.getRootParent();

            if (rootParent instanceof CopyPhiStmt) {
                buildRing(cfg, ((CopyPhiStmt) rootParent).getVariable().getLocal(), currentRing);
            }
        }
    }

    public LocalVariableRing getRingOf(Local local) {
        return this.ringMap.get(local);
    }

    public HashSet<LocalVariableRing> getRings() {
        return rings;
    }

    public static final class LocalVariableRing {
        private final HashSet<Local> variables;

        public LocalVariableRing(HashSet<Local> variables) {
            this.variables = variables;
        }

        public HashSet<Local> getVariables() {
            return variables;
        }
    }
}
