package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CycleDetector {
    private final Set<BasicBlock> cyclicBlocks;

    public CycleDetector(ControlFlowGraph cfg) {
        this.cyclicBlocks = Collections.unmodifiableSet(findCycledBlocks(cfg));
    }

    private void visit(ControlFlowGraph cfg, BasicBlock block, HashSet<BasicBlock> visitedBlocks, HashSet<BasicBlock> cyclicBlocks) {
        if (!visitedBlocks.add(block)) {
            cyclicBlocks.add(block);
            return;
        }

        cfg.getSuccessors(block).forEach(succ -> visit(cfg, succ, visitedBlocks, cyclicBlocks));
    }

    private HashSet<BasicBlock> findCycledBlocks(ControlFlowGraph cfg) {
        HashSet<BasicBlock> cyclicBlocks = new HashSet<>();
        HashSet<BasicBlock> visitedBlocks = new HashSet<>();

        visit(cfg, cfg.getEntry(), visitedBlocks, cyclicBlocks);

        return cyclicBlocks;
    }

    public Set<BasicBlock> getCyclicBlocks() {
        return cyclicBlocks;
    }
}
