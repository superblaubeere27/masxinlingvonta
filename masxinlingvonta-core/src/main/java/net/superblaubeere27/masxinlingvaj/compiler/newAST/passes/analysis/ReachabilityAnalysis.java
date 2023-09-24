package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.BranchSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.GenericBitSet;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

public class ReachabilityAnalysis {
    private final GenericBitSet<BasicBlock> bitSet;

    public ReachabilityAnalysis(ControlFlowGraph cfg) {
        this.bitSet = cfg.createBitSet();

        this.dfs(cfg.getEntry(), cfg::getEdges);
    }

    /**
     * Analyzes if the blocks can <i>actually</i> be reached (according to local variable analysis)
     */
    public ReachabilityAnalysis(ControlFlowGraph cfg, LocalVariableAnalyzer analyzer) {
        this.bitSet = cfg.createBitSet();

        this.dfs(cfg.getEntry(), (block) -> {
            var terminator = block.getTerminator();

            if (!(terminator instanceof BranchStmt branchStmt))
                return Collections.emptySet();

            var knownTargetIdx = BranchSimplifier.getBranchTargetIndexIfKnown(analyzer.simplifier, branchStmt, analyzer.getStatementSnapshot(branchStmt), false);

            return knownTargetIdx.<Set<FlowEdge<BasicBlock>>>
                            map(integer -> Collections.singleton(new BasicFlowEdge(block, branchStmt.getNextBasicBlocks()[integer])))
                    .orElseGet(() -> cfg.getEdges(block));
        });
    }

    private void dfs(BasicBlock block, Function<BasicBlock, Set<FlowEdge<BasicBlock>>> edgeFunction) {
        if (!this.bitSet.add(block)) {
            return;
        }

        for (FlowEdge<BasicBlock> edge : edgeFunction.apply(block)) {
            this.dfs(edge.dst(), edgeFunction);
        }
    }

    public boolean isReachable(BasicBlock block) {
        return this.bitSet.contains(block);
    }

}
