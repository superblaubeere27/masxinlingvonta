package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.RedundantExpressionAndAssignmentRemover;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;

import java.sql.Array;
import java.util.*;

public class StatementTransaction {
    private final HashMap<Expr, Expr> exprReplacements = new HashMap<>();
    private final HashMap<Stmt, List<Stmt>> replacements = new HashMap<>();
    private final HashSet<BasicBlock> removedBlocks = new HashSet<>();

    public void replaceStatement(Stmt stmt, List<Stmt> replacements) {
        if (this.replacements.put(stmt, replacements) != null) {
            throw new IllegalStateException("There is already a replacement for this statement");
        }
    }

    public void exciseBlockIfUnreferenced(BasicBlock block) {
        if (block.cfg.getReverseEdges(block).isEmpty())
            this.removeBlock(block);
    }

    private void removeBlock(BasicBlock block) {
        this.removedBlocks.add(block);
    }

    public void replaceStatement(Stmt stmt, Stmt replacement) {
        this.replaceStatement(stmt, Collections.singletonList(replacement));
    }

    public void replaceExpr(Expr from, Expr to) {
        if (this.exprReplacements.put(from, to) != null)
            throw new IllegalStateException();
    }

    public void replaceStatementAndExtractSideEffects(Stmt stmt, Stmt replacement) {
        var extractedExpressions = RedundantExpressionAndAssignmentRemover.extractNonRedundantExpressions(stmt);

        var replacements = new ArrayList<Stmt>(extractedExpressions.size() + 1);

        extractedExpressions.forEach(x -> replacements.add(new ExpressionStmt(x.copy())));
        replacements.add(replacement);

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

        var blockRemovalQueue = new ArrayDeque<>(this.removedBlocks);

        while (!blockRemovalQueue.isEmpty()) {
            var blockToRemove = blockRemovalQueue.pop();
            var cfg = blockToRemove.getGraph();

            // Remember outgoing edges
            var outgoingEdges = new ArrayList<>(cfg.getEdges(blockToRemove));

            cfg.exciseBlock(blockToRemove);

            // Is any successive block left unreferenced? Then excise it too
            for (FlowEdge<BasicBlock> outgoingEdge : outgoingEdges) {
                if (cfg.getReverseEdges(outgoingEdge.dst()).isEmpty()) {
                    blockRemovalQueue.push(outgoingEdge.dst());
                }
            }
        }

        boolean changed = !replacements.isEmpty() || !this.removedBlocks.isEmpty() || !this.exprReplacements.isEmpty();

        this.replacements.clear();

        return changed;
    }

}
