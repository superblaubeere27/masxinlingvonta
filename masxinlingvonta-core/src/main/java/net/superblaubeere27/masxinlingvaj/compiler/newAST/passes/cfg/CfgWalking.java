package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.cfg;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.BranchSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.RedundantExpressionAndAssignmentRemover;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.ExpressionSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.DeleteRefStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CfgWalking {
    private final MLVCompiler compiler;

    public CfgWalking(MLVCompiler compiler) {
        this.compiler = compiler;
    }

    public boolean apply(ControlFlowGraph cfg) {
        var topoOrderBlocks = cfg.verticesInOrder();

        StatementTransaction transaction = new StatementTransaction();
        LocalVariableAnalyzer analyzer = new LocalVariableAnalyzer(cfg);

        analyzer.analyze();

        boolean changed = false;

        for (int i = topoOrderBlocks.size() - 1; i >= 0; i--) {
            var block = topoOrderBlocks.get(i);

            if (!(block.getTerminator() instanceof BranchStmt branch))
                continue;

            // Speculatively execute every next block that might come
            for (int j = 0; j < branch.getNextBasicBlocks().length; j++) {
                branchWalk(analyzer, transaction, block, j, branch);

                var c = transaction.apply();

                changed |= c;
            }

        }

        return changed;
    }

    private void branchWalk(LocalVariableAnalyzer analyzer, StatementTransaction transaction, BasicBlock fromBlock, int branchIdx, BranchStmt branch) {
        BasicBlock toBlock = branch.getNextBasicBlocks()[branchIdx];
        CfgWalker cfgWalker = new CfgWalker(analyzer);

        var snapshot = LocalVariableAnalyzer.getSuccessiveBlockSnapshot(analyzer.getStatementSnapshot(branch), branch, branchIdx);

        // Run the speculative execution, return if there wasn't a usable result
        if (!cfgWalker.branchWalkRecursive(fromBlock, toBlock, snapshot) || !cfgWalker.canReplace()) {
            return;
        }

        // To prevent loops, don't allow jumps through this block
        if (cfgWalker.walkedBlocks.stream().anyMatch(x -> x.dst().equals(fromBlock))) {
            return;
        }

        // We can do an easier operation on unconditional branches
        if (branch instanceof UnconditionalBranch unconditionalBranch) {
            cfgWalker.fixPhis(fromBlock);

            transaction.replaceStatement(unconditionalBranch, cfgWalker.getReplacements());
            transaction.exciseEdgeAndUnreferencedBlocks(new BasicFlowEdge(fromBlock, toBlock));
            return;
        }

        boolean endsWithBranch = cfgWalker.replacements.get(cfgWalker.getReplacements().size() - 1) instanceof BranchStmt;
        boolean skippedCondBranches = cfgWalker.walkedBlocks.stream().filter(x -> !(x.src().getTerminator() instanceof UnconditionalBranch)).count() > 1;

        // If there was no branch skipped and no values replaced, this optimization is useless since it would just
        // readd this existing block. If the last statement is a branch, we would expect at least 2 walked blocks
        if ((cfgWalker.walkedBlocks.size() < 2) && cfgWalker.phiReplacements.isEmpty()) {
            return;
        }

        // For conditional branches we need to introduce a new basic block
        BasicBlock newTarget = new BasicBlock(fromBlock.getGraph());

        newTarget.addAll(cfgWalker.getReplacements());

        cfgWalker.fixPhis(newTarget);

        transaction.replaceBranchTarget(branch, branchIdx, newTarget);
    }

    private class CfgWalker {
        private final LocalVariableAnalyzer analyzer;
        private final ArrayList<Stmt> replacements = new ArrayList<>();
        /**
         * When a phi is encountered while walking, the actual value of the phi local is saved here.
         * E.g. %5 = phi [L1, 5], [L2, 10]; If we come from L2, this map would contain [%5 -> 10]
         */
        private HashMap<Local, Expr> phiReplacements = new HashMap<>();
        private final ArrayList<BasicFlowEdge> walkedBlocks = new ArrayList<>();

        /**
         * If the replacement ends with a branch, this edge tells which edge this branch had originally taken
         */
        private BasicFlowEdge finalEdge = null;

        private CfgWalker(LocalVariableAnalyzer analyzer) {
            this.analyzer = analyzer;
        }

        private boolean branchWalkRecursive(BasicBlock from, BasicBlock block, LocalInfoSnapshot snapshot) {
            if (this.walkedBlocks.contains(new BasicFlowEdge(from, block)))
                return false;

            this.walkedBlocks.add(new BasicFlowEdge(from, block));

            for (Stmt stmt : block) {
                if (stmt instanceof DeleteRefStmt) {
                    // This statement is allowed, we can just add it to the replacements
                    this.replacements.add(stmt);
                } else if (stmt instanceof CopyPhiStmt) {
                    // Remember which argument we would use as phi param
                    this.phiReplacements.put(((CopyPhiStmt) stmt).getVariable().getLocal(), ((CopyPhiStmt) stmt).getExpression().getArgument(from));
                } else if (stmt instanceof RetStmt retStmt) {
                    this.replacements.add(stmt);

//                    return retStmt.getValue() instanceof ConstExpr || retStmt.getValue() instanceof VarExpr varExpr && this.phiReplacements.containsKey(varExpr.getLocal());
                    return true;
                } else if (stmt instanceof RetVoidStmt) {
                    this.replacements.add(stmt);

                    return true;
                } else if (stmt instanceof BranchStmt branchStmt) {
                    var nextTargetIdx = BranchSimplifier.getBranchTargetIndexIfKnown(new ExpressionSimplifier(CfgWalking.this.compiler.getIndex()), branchStmt, snapshot, true);

                    if (nextTargetIdx.isEmpty()) {
                        return false;
                    }

                    var nextTarget = branchStmt.getNextBasicBlocks()[nextTargetIdx.get()];

                    // The assumption handling is currently not expecting that any of the replacement-instructions to have side effects.
                    for (Expr child : branchStmt.enumerateOnlyChildren()) {
                        if (RedundantExpressionAndAssignmentRemover.hasSideEffects(child))
                            return false;
                    }

                    var prevPhiReplacements = (HashMap<Local, Expr>) this.phiReplacements.clone();
                    var prevReplacementSize = this.replacements.size();

                    var lastWalkedBasicBlocksSize = this.walkedBlocks.size();

                    if (!this.branchWalkRecursive(block, nextTarget, LocalVariableAnalyzer.getSuccessiveBlockSnapshot(snapshot, branchStmt, nextTargetIdx.get()))) {
                        // If we encountered a phi or the next block contains a phi, we would need to do serious phi refactoring and I am too lazy
                        // to implement that
                        if (!prevPhiReplacements.isEmpty()
//                                || nextTargetIdx.get(0).getOpcode() == Opcode.PHI_STORE
                        )
                            return false;

                        // The branchWalkRecursive method might have added invalid replacement instructions. These are
                        // invalid now and have to be removed.
                        truncateListToSize(this.replacements, prevReplacementSize);
                        // Same with walked blocks
                        truncateListToSize(this.walkedBlocks, lastWalkedBasicBlocksSize);


                        this.phiReplacements = prevPhiReplacements;

                        this.finalEdge = new BasicFlowEdge(block, nextTarget);

                        this.replacements.add(new UnconditionalBranch(nextTarget));
                    }

                    return true;
                } else {
                    return false;
                }
            }

            return false;
        }

        private void truncateListToSize(List<?> list, int toSize) {
            int removedTailSize = list.size() - toSize;

            for (int i = 0; i < removedTailSize; i++) {
                list.remove(list.size() - 1);
            }
        }

        private boolean canReplace() {
            return !this.replacements.isEmpty() && this.replacements.size() < 5;
        }

        private List<Stmt> getReplacements() {
            return this.replacements.stream().map(x -> replacePhiValues(x.copy())).toList();
        }

        /**
         * If a branch was forwarded, some phi nodes might need to be fixed since there was an additional incoming
         * edge added.
         * <p>
         * For example:
         * <code>
         * L1:
         * br L2
         * L2:
         * br L3:
         * L3:
         * phi [L0, 1], [L2, 2]
         * </code>
         * <p>
         * Here the phi needs to be changed to <code>phi [L0, 1], [L1, 2], [L2, 2]</code> if <code>br L3</code> of
         * L1 was forwarded
         */
        private void fixPhis(BasicBlock newParentBlock) {
            if (this.finalEdge == null)
                return;

            for (Stmt stmt : this.finalEdge.dst()) {
                if (!(stmt instanceof CopyPhiStmt copyPhiStmt))
                    continue;

                PhiExpr phiExpr = copyPhiStmt.getExpression();

                // Get the argument for the final edge
                var argument = Objects.requireNonNull(phiExpr.getArgument(this.finalEdge.src()));

                // Refactor it and copy it to the new parent block
                phiExpr.setArgument(newParentBlock, this.replacePhiValues(argument.copy()));
            }
        }

        private <T extends CodeUnit> T replacePhiValues(T copy) {
            // Replace the phi values
            for (Expr child : copy.enumerateOnlyChildren()) {
                if (child instanceof VarExpr varExpr) {
                    var replacement = this.phiReplacements.get(varExpr.getLocal());

                    // Is there a replacement for this local?
                    if (replacement == null)
                        continue;

                    copy.replaceExpr(child, replacement.copy());
                }
            }

            return copy;
        }
    }

}
