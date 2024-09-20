package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ReachabilityAnalysis;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.deadCode.DeadCodeRemover;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;

import javax.annotation.Nullable;
import java.util.*;

public class StatementTransaction {
    private final HashMap<Expr, Expr> exprReplacements = new HashMap<>();
    private final HashMap<Stmt, List<Stmt>> replacements = new HashMap<>();
    /**
     * Blocks that should be removed if they become unreferenced.
     */
    private final HashSet<BasicBlock> removedBlocks = new HashSet<>();

    public void replaceStatement(Stmt stmt, List<Stmt> replacements) {
        if (this.replacements.put(stmt, replacements) != null) {
            throw new IllegalStateException("There is already a replacement for this statement");
        }
    }

    /**
     * Removes blocks and their edges. If blocks become unreachable, remove their children too.
     *
     * @param removedBlocks must contain elements
     * @return true if any changes where made
     */
    private static boolean applyBlockRemovals(HashSet<BasicBlock> removedBlocks) {
        var blockRemovalQueue = new ArrayDeque<>(removedBlocks);
        var cfg = blockRemovalQueue.peekFirst().cfg;
        var reachabilityAnalysis = new ReachabilityAnalysis(cfg);

        var changed = false;

        while (!blockRemovalQueue.isEmpty()) {
            var blockToRemove = blockRemovalQueue.pop();

            assert blockToRemove.getGraph() == cfg;

            // Don't remove reachable blocks
            if (reachabilityAnalysis.isReachable(blockToRemove))
                continue;

            // Remember outgoing edges
            var outgoingEdges = new ArrayList<>(cfg.getEdges(blockToRemove));

            if (blockToRemove.getGraph().containsVertex(blockToRemove)) {
                cfg.exciseBlock(blockToRemove);

                changed = true;
            }

            // Is any successive block left unreferenced? Then excise it too
            for (FlowEdge<BasicBlock> outgoingEdge : outgoingEdges) {
                if (!reachabilityAnalysis.isReachable(outgoingEdge.dst())) {
                    blockRemovalQueue.push(outgoingEdge.dst());
                }
            }
        }

        return changed;
    }

    /**
     * Excises the edge from the cfg.
     * If the next block becomes unreferenced due to this, remove it too alongside with its edges (recursively)
     */
    public void exciseEdgeAndUnreferencedBlocks(FlowEdge<BasicBlock> edge) {
        edge.dst().getGraph().exciseEdge(edge);

        exciseBlockIfUnreferenced(edge.dst());
    }

    public void replaceStatement(Stmt stmt, Stmt replacement) {
        this.replaceStatement(stmt, Collections.singletonList(replacement));
    }

    public void replaceExpr(Expr from, Expr to) {
        if (this.exprReplacements.put(from, to) != null)
            throw new IllegalStateException();
    }

    /**
     * Excises the block if it is currently unreferenced. If it is excised, the following edges are removed too
     */
    public void exciseBlockIfUnreferenced(BasicBlock block) {
        this.removedBlocks.add(block);
    }

    public void removeStatementAndExtractSideEffects(Stmt stmt) {
        this.replaceStatementAndExtractSideEffects(stmt, null);
    }

    public void replaceStatementAndExtractSideEffects(Stmt stmt, @Nullable Stmt replacement) {
        var extractedExpressions = DeadCodeRemover.extractNonRedundantExpressions(stmt);

        var replacements = new ArrayList<Stmt>(extractedExpressions.size() + 1);

        extractedExpressions.forEach(x -> replacements.add(new ExpressionStmt(x.copy())));

        if (replacement != null) {
            replacements.add(replacement);
        }

        this.replaceStatement(stmt, replacements);
    }

    /**
     * Applies the transformations done.
     *
     * @return true if any changed where made
     */
    public boolean apply() {
        for (Map.Entry<Stmt, List<Stmt>> replacedStatements : replacements.entrySet()) {
            BasicBlock block = replacedStatements.getKey().getBlock();

            int idx = block.indexOf(replacedStatements.getKey());

            block.remove(idx);

            block.addAll(idx, replacedStatements.getValue());
        }

        exprReplacements.forEach((from, to) -> {
            from.getBlock().getGraph().writeAt(from.getParent(), from, to);
        });

        boolean changed = !replacements.isEmpty() || !this.exprReplacements.isEmpty();

        if (!this.removedBlocks.isEmpty()) {
            changed |= applyBlockRemovals(this.removedBlocks);
        }

        this.replacements.clear();
        this.exprReplacements.clear();
        this.removedBlocks.clear();

        return changed;
    }

    /**
     * Replaces branch target in given index
     */
    public void replaceBranchTarget(BranchStmt branch, int branchIdx, BasicBlock newTarget) {
        var oldTarget = branch.getNextBasicBlocks()[branchIdx];

        branch.getNextBasicBlocks()[branchIdx] = newTarget;

        // Check if the edge is still active, otherwise excise.
        if (Arrays.stream(branch.getNextBasicBlocks()).noneMatch(x -> x.equals(oldTarget))) {
            this.exciseEdgeAndUnreferencedBlocks(new BasicFlowEdge(branch.getBlock(), oldTarget));
        }

        // Add the newly created edge.
        newTarget.getGraph().addEdge(new BasicFlowEdge(branch.getBlock(), newTarget));
    }
}
