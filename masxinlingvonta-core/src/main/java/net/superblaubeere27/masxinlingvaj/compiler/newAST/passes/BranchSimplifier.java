package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstBoolExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.Arrays;
import java.util.Map;

public class BranchSimplifier {

    public static void trySimplifyBranch(LocalVariableAnalyzer analyzer, StatementTransaction transaction, BranchStmt branch) {
        // Check if a branch has only one target
        // i.e.: br i == 0 if L1 else L1 => br L!
        if (!(branch instanceof UnconditionalBranch)) {
            BasicBlock nextBasicBlock = branch.getNextBasicBlocks()[0];

            if (Arrays.stream(branch.getNextBasicBlocks()).allMatch(nextBasicBlock::equals)) {
                transaction.replaceStatement(branch, new UnconditionalBranch(nextBasicBlock));

                return;
            }
        }

        if (branch instanceof ExceptionCheckStmt) {
            var exceptionCheckStmt = ((ExceptionCheckStmt) branch);

            var exceptionTarget = exceptionCheckStmt.getExceptionTarget();
            var okTarget = exceptionCheckStmt.getOkTarget();

            var exceptionState = analyzer.getStatementSnapshot(branch).getCallGraphState().getExceptionState();

            if (exceptionTarget.equals(okTarget)) {
                transaction.replaceStatement(branch, new UnconditionalBranch(okTarget));
            } else if (!exceptionState.isUnknown()) {
                var trueTarget = exceptionState.getAssumedValue() ? exceptionTarget : okTarget;
                var notTarget = exceptionState.getAssumedValue() ? okTarget : exceptionTarget;

                notTarget.getGraph().exciseEdge(new BasicFlowEdge(branch.getBlock(), notTarget));

                transaction.exciseBlockIfUnreferenced(notTarget);
                transaction.replaceStatement(branch, new UnconditionalBranch(trueTarget));
            } else if (isEquivalentForExceptionCheckBranch(okTarget, exceptionTarget)) {
                exceptionTarget.getGraph().exciseEdge(new BasicFlowEdge(branch.getBlock(), exceptionTarget));

                transaction.exciseBlockIfUnreferenced(exceptionTarget);

                transaction.replaceStatement(branch, new UnconditionalBranch(okTarget));
            }
        } else if (branch instanceof ConditionalBranch) {
            var conditionalBranch = (ConditionalBranch) branch;

            if (conditionalBranch.getIfTarget().equals(conditionalBranch.getElseTarget())) {
                transaction.replaceStatementAndExtractSideEffects(branch, new UnconditionalBranch(conditionalBranch.getIfTarget()));
            } else if (conditionalBranch.getCond() instanceof ConstBoolExpr) {
                boolean trueValue = ((ConstBoolExpr) conditionalBranch.getCond()).getValue();

                var trueTarget = trueValue ? conditionalBranch.getIfTarget() : conditionalBranch.getElseTarget();
                var notTarget = trueValue ? conditionalBranch.getElseTarget() : conditionalBranch.getIfTarget();

                notTarget.getGraph().exciseEdge(new BasicFlowEdge(branch.getBlock(), notTarget));

                transaction.exciseBlockIfUnreferenced(notTarget);
                transaction.replaceStatement(branch, new UnconditionalBranch(trueTarget));
            }
        }

        // Simplify branches if the next block will just do the same jump. In the following case we could just straight
        // jump to L6 if no exception occured.
        // L1:
        //    br L2, on_exception L34
        // L2:
        //    br L6, on_exception L34
//        for (int i = 0; i < branch.getNextBasicBlocks().length; i++) {
//            var nextBlock = branch.getNextBasicBlocks()[i];
//
//            var firstInstruction = nextBlock.get(0);
//
//            if (!(firstInstruction instanceof BranchStmt))
//                continue;
//
//            // Check if the branches have equivalent conditions
//            if (!((BranchStmt) firstInstruction).isConditionEquivalent(branch))
//                continue;
//
//            var nextBranchBasicBlock = ((BranchStmt) firstInstruction).getNextBasicBlocks()[i];
//
//            // copy the branch and change the target to the next branches target
//            var branchCopy = (BranchStmt) branch.copy();
//
//            branchCopy.getNextBasicBlocks()[i] = nextBranchBasicBlock;
//
//            if (Arrays.stream(branchCopy.getNextBasicBlocks()).noneMatch(x -> x.equals(nextBlock))) {
//                nextBlock.getGraph().exciseEdge(new BasicFlowEdge(branch.getBlock(), nextBlock));
//
//                transaction.exciseBlockIfUnreferenced(nextBlock);
//            }
//
//            for (Stmt stmt : nextBranchBasicBlock) {
//                if (!(stmt instanceof CopyPhiStmt phi))
//                    break;
//
//                phi.getExpression().setArgument(nextBranchBasicBlock, phi.getExpression().getArgument(nextBlock).copy());
//            }
//
//            transaction.replaceStatement(branch, branchCopy);
//        }
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
