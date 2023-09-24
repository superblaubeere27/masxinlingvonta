package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstBoolExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.ExpressionSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.Arrays;
import java.util.Optional;

public class BranchSimplifier {

    public static void trySimplifyBranch(ExpressionSimplifier simplifier, LocalVariableAnalyzer analyzer, StatementTransaction transaction, BranchStmt branch) {
        var actualTargetIndex = getBranchTargetIndexIfKnown(simplifier, branch, analyzer.getStatementSnapshot(branch), false);

        if (actualTargetIndex.isEmpty())
            return;

        var actualTarget = branch.getNextBasicBlocks()[actualTargetIndex.get()];

        // Remove references to basic blocks that will be untargeted and excise them if needed
        for (BasicBlock nextBasicBlock : branch.getNextBasicBlocks()) {
            if (nextBasicBlock.equals(actualTarget)) {
                continue;
            }

            transaction.exciseEdgeAndUnreferencedBlocks(new BasicFlowEdge(branch.getBlock(), nextBasicBlock));
        }

        transaction.replaceStatementAndExtractSideEffects(branch, new UnconditionalBranch(actualTarget));
    }

    /**
     * Returns the index of the branch that is taken.
     */
    public static Optional<Integer> getBranchTargetIndexIfKnown(ExpressionSimplifier simplifier, BranchStmt branch, LocalInfoSnapshot snapshot, boolean unconditionalCounts) {
        // Check if a branch has only one target
        // i.e.: br i == 0 if L1 else L1 => br L1
        if (!(branch instanceof UnconditionalBranch)) {
            BasicBlock firstNextBasicBlock = branch.getNextBasicBlocks()[0];

            if (Arrays.stream(branch.getNextBasicBlocks()).allMatch(firstNextBasicBlock::equals)) {
                // All branch targets are equal, return the first
                return Optional.of(0);
            }
        }

        if (branch instanceof ExceptionCheckStmt exceptionCheckStmt) {
            var exceptionTarget = exceptionCheckStmt.getExceptionTarget();
            var okTarget = exceptionCheckStmt.getOkTarget();

            var exceptionState = snapshot.getCallGraphState().getExceptionState();

            if (!exceptionState.isUnknown()) {
                return Optional.of(exceptionState.getAssumedValue() ? ExceptionCheckStmt.ON_EXCEPTION_IDX : ExceptionCheckStmt.ON_OK_IDX);
            } else if (isEquivalentForExceptionCheckBranch(okTarget, exceptionTarget)) {
                return Optional.of(ExceptionCheckStmt.ON_OK_IDX); // Return the ok-target
            }
        } else if (branch instanceof ConditionalBranch conditionalBranch) {
            if ((simplifier == null ? conditionalBranch.getCond() : simplifier.simplifyExpressionIfPossible(snapshot, conditionalBranch.getCond())) instanceof ConstBoolExpr constBoolExpr) {
                boolean trueValue = constBoolExpr.getValue();

                return Optional.of(trueValue ? ConditionalBranch.ON_IF_IDX : ConditionalBranch.ON_ELSE_IDX);
            }
        } else if (branch instanceof UnconditionalBranch unconditionalBranch && unconditionalCounts) {
            return Optional.of(0);
        }

        return Optional.empty();
    }

    private static boolean isEquivalentForExceptionCheckBranch(BasicBlock first, BasicBlock second) {
        if (first.size() != second.size())
            return false;

        for (int i = 0; i < first.size(); i++) {
            var firstStmt = first.get(i);
            var secondStmt = second.get(i);

            if (((firstStmt.getOpcode() != Opcode.RET && firstStmt.getOpcode() != Opcode.RET_VOID) || firstStmt.getOpcode() != secondStmt.getOpcode()) && !firstStmt.equivalent(secondStmt)) {
                return false;
            }
        }

        return true;
    }

}
