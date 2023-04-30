package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.utils.CustomEquivalenceMap;

import java.util.*;
import java.util.stream.Stream;

public class CallGraphPruningPass extends Pass {

    private static boolean isUsedByPhi(BasicBlock block) {
        return block.getGraph().getSuccessors(block).anyMatch(successor -> {
            for (Stmt stmt : successor) {
                if (!(stmt instanceof CopyPhiStmt)) {
                    break;
                }

                if (((CopyPhiStmt) stmt).getExpression().getSources().contains(block))
                    return true;
            }

            return false;
        });
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        boolean changed;

        do {
            changed = replaceDuplicates(cfg);
//
            changed |= mergeBlocks(cfg);
        } while (changed);
    }

    private boolean mergeBlocks(ControlFlowGraph cfg) {
        var prependedBlocks = new HashMap<BasicBlock, BasicBlock>();

        var alreadyAffected = new HashSet<BasicBlock>();

        for (BasicBlock vertex : cfg.vertices()) {
            // Don't merge blocks two blocks at once
            if (alreadyAffected.contains(vertex))
                continue;

            var nextBlocks = cfg.getEdges(vertex);

            if (nextBlocks.size() != 1 || !(vertex.getTerminator() instanceof UnconditionalBranch || vertex.getTerminator() instanceof ExceptionCheckStmt) || (cfg.getEntries().contains(vertex) && isUsedByPhi(vertex)) || !canRefactorPhis(vertex))
                continue;

            BasicBlock nextBlock = nextBlocks.iterator().next().dst();

            // Don't try to merge a block with itself
            if (vertex.equals(nextBlock))
                continue;

            // Don't merge blocks two blocks at once
            if (alreadyAffected.contains(nextBlock))
                continue;

            // Blocks made out of single branches can always be forwarded
            if ((vertex.size() != 1 || vertex.getTerminator().getOpcode() != Opcode.UNCONDITIONAL_BRANCH) && cfg.getReverseEdges(nextBlock).size() != 1)
                continue;

            // Forward phi values
            this.refactorPhis(vertex);

            prependedBlocks.put(vertex, nextBlock);

            alreadyAffected.add(vertex);
            alreadyAffected.add(nextBlock);
        }

        for (Map.Entry<BasicBlock, BasicBlock> prependedBlock : prependedBlocks.entrySet()) {
            ArrayList<Stmt> prependedStmts = new ArrayList<>();

            for (int i = 0; i < prependedBlock.getKey().size() - 1; i++) {
                prependedStmts.add(prependedBlock.getKey().get(i).copy());
            }

            prependedBlock.getValue().addAll(0, prependedStmts);
        }

        cfg.refactorBasicBlocks(prependedBlocks);

        return !prependedBlocks.isEmpty();
    }

    private boolean canRefactorPhis(BasicBlock blockToRemove) {
        var preds = blockToRemove.getGraph().getReverseEdges(blockToRemove);
        var succs = blockToRemove.getGraph().getEdges(blockToRemove);

        for (FlowEdge<BasicBlock> edge : succs) {
            var isInvalid = phiStream(edge.dst()).anyMatch(phi -> {
                var expectedArg = phi.getArgument(blockToRemove);

                for (FlowEdge<BasicBlock> pred : preds) {
                    var currArg = phi.getArgument(pred.src());

                    if (currArg != null && !currArg.equivalent(expectedArg)) {
                        return true;
                    }
                }

                return false;
            });

            if (isInvalid)
                return false;
        }

        return true;
    }

    private void refactorPhis(BasicBlock blockToRemove) {
        var preds = blockToRemove.getGraph().getReverseEdges(blockToRemove);
        var succs = blockToRemove.getGraph().getEdges(blockToRemove);

        for (FlowEdge<BasicBlock> edge : succs) {
            phiStream(edge.dst()).forEachOrdered(phi -> {
                var commonArg = phi.getArgument(blockToRemove);

                for (FlowEdge<BasicBlock> pred : preds) {
                    phi.setArgument(pred.src(), commonArg.copy());
                }

                phi.removeArgument(blockToRemove);
            });
        }
    }

    private Stream<PhiExpr> phiStream(BasicBlock dst) {
        return dst.stream().takeWhile(x -> x instanceof CopyPhiStmt).map(x -> ((CopyPhiStmt) x).getExpression());
    }

    private boolean replaceDuplicates(ControlFlowGraph cfg) {
        var basicBlocks = new CustomEquivalenceMap<List<Stmt>, List<BasicBlock>>((a, bRaw) -> {
            var b = (List<Stmt>) bRaw;

            if (a.size() != b.size())
                return false;

            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).equivalent(b.get(i)))
                    return false;
            }

            return true;
        });

        // Group by statements
        for (BasicBlock vertex : cfg.vertices()) {
            if (isUsedByPhi(vertex) || cfg.getEntries().contains(vertex))
                continue;

            basicBlocks.computeIfAbsent(vertex.getStatements(), key -> new ArrayList<>()).add(vertex);
        }

        var mappings = new HashMap<BasicBlock, BasicBlock>();

        basicBlocks.values()
                .stream()
                .filter(x -> !x.isEmpty())
                .forEach(blocks -> {
                    var first = blocks.get(0);

                    for (int i = 1; i < blocks.size(); i++) {
                        mappings.put(blocks.get(i), first);
                    }
                });

        cfg.refactorBasicBlocks(mappings);

        return !mappings.isEmpty();
    }

    private boolean forwardBlocks(ControlFlowGraph cfg) {
        HashMap<BasicBlock, BasicBlock> forwardedBlocks = new HashMap<>();

        for (BasicBlock block : cfg.vertices()) {
            if (block.size() != 1 || !(block.get(0) instanceof UnconditionalBranch)) {
                continue;
            }

            BasicBlock target = ((UnconditionalBranch) block.get(0)).getTarget();

            // Never forward unconditional loops
            if (target.equals(block)) {
                continue;
            }

            if (isUsedByPhi(block))
                continue;

            forwardedBlocks.put(block, target);
        }

        boolean modified;

        do {
            modified = false;

            for (Map.Entry<BasicBlock, BasicBlock> forwarding : forwardedBlocks.entrySet()) {
                if (forwardedBlocks.containsKey(forwarding.getValue())) {
                    forwardedBlocks.put(forwarding.getKey(), forwardedBlocks.get(forwarding.getValue()));

                    modified = true;
                }
            }
        } while (modified);

        cfg.refactorBasicBlocks(forwardedBlocks);

        return !forwardedBlocks.isEmpty();
    }

}
