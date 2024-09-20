package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.reuseLocals;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.LT79Dom;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutFieldStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutStaticStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.GenericBitSet;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.HashSet;
import java.util.List;

public class ReuseLocalsPass extends Pass {
    private static boolean containsWrite(Stmt stmtBeforeStmt) {
        // TODO: Make this more specific. We don't care about all writes in every situation.
        if (stmtBeforeStmt instanceof PutFieldStmt || stmtBeforeStmt instanceof PutStaticStmt) {
            return true;
        }

        for (Expr enumerateOnlyChild : stmtBeforeStmt.enumerateOnlyChildren()) {
            if (enumerateOnlyChild.getMetadata().getProperties().stream().anyMatch(x -> x.conflictsWith(ReadsMemoryProperty.INSTANCE)))
                return true;
        }

        return false;
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        var analyzer = new LocalVariableAnalyzer(cfg);
        var dominanceAnalyzer = new LT79Dom<>(cfg, cfg.getEntry());

        var reuseLocalManager = new ReusableLocalManager(cfg);

        analyzer.analyze();

        var transaction = new StatementTransaction();

        // When we reused a variable, don't like to copy-statement that has already been replaced.
        var invalidCandidates = new HashSet<Local>();

        for (BasicBlock block : cfg.vertices()) {
            for (Stmt stmt : block) {
                if (!(stmt instanceof CopyVarStmt copyVarStmt)) {
                    continue;
                }

                var expression = copyVarStmt.getExpression();

                var possibleCandidates = reuseLocalManager.getReuseCandidatesFor(expression);

                if (possibleCandidates == null)
                    continue;

                var validCandidate = findValidCandidate(cfg, dominanceAnalyzer, invalidCandidates, copyVarStmt, possibleCandidates);

                if (validCandidate == null)
                    continue;

                transaction.replaceExpr(copyVarStmt.getExpression(), validCandidate.replacementValue().copy());
                invalidCandidates.add(copyVarStmt.getVariable().getLocal());
            }
        }

        transaction.apply();
    }

    private ReusableLocalManager.ReuseCandidate findValidCandidate(ControlFlowGraph cfg, LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dom, HashSet<Local> replacedCandidates, CopyVarStmt stmt, List<ReusableLocalManager.ReuseCandidate> possibleCandidates) {
        for (ReusableLocalManager.ReuseCandidate possibleCandidate : possibleCandidates) {
            if (possibleCandidate.replacementValue() instanceof VarExpr declaringVar) {
                var declaringLocal = declaringVar.getLocal();

                if (replacedCandidates.contains(declaringLocal))
                    continue;
                if (stmt.getVariable().getLocal().equals(declaringLocal))
                    continue;
            }

            var candidateDeclaringStatement = possibleCandidate.declaringStmt();

            // A candidate is not viable if it comes after the usage
            if (!dominates(dom, stmt, candidateDeclaringStatement))
                continue;

            if (possibleCandidate.identifier().allowsMemoryWrite())
                return possibleCandidate;

            if (blockWritesBetween(stmt, candidateDeclaringStatement))
                continue;

            return possibleCandidate;
        }

        return null;
    }

    /**
     * Checks if <code>dominated</code> is dominated by <code>possibleDominator</code>
     */
    private boolean dominates(LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dom, Stmt dominated, Stmt possibleDominator) {
        var dominatorBlock = possibleDominator.getBlock();

        if (!dom.getDominates(dominatorBlock).contains(dominated.getBlock()))
            return false;

        if (dominatorBlock == dominated.getBlock())
            return dominatorBlock.indexOf(dominated) >= dominatorBlock.indexOf(possibleDominator);

        return true;
    }

    private boolean blockWritesBetween(Stmt stmt, Stmt defStmt) {
        // Check if the statements before stmt write memory
        for (Stmt stmtBeforeStmt : stmt.getBlock()) {
            if (stmtBeforeStmt == stmt) {
                break;
            }

            if (containsWrite(stmtBeforeStmt))
                return true;
        }
        // Check if the statements before defStmt write memory
        var statementsAfterDef = defStmt.getBlock().getStatements();

        for (int i = statementsAfterDef.size() - 1; i >= 0; i--) {
            var stmtAfterStmt = statementsAfterDef.get(i);

            if (stmtAfterStmt == defStmt) {
                break;
            }

            if (containsWrite(stmtAfterStmt))
                return true;
        }

        var defBlock = defStmt.getBlock();

        return blockWritesBetween0(stmt.getBlock(), defBlock, defBlock.getGraph().createBitSet(), true);
    }

    private boolean blockWritesBetween0(BasicBlock dominated, BasicBlock dominator, GenericBitSet<BasicBlock> visitedBlocks, boolean first) {
        if (dominator.equals(dominated)) {
            return false;
        }
        if (!visitedBlocks.add(dominated)) {
            return false;
        }

        // Check if any of the statements contains a write. If it is the first block, don't check because the check
        // was already performed in blockWritesBetween.
        if (!first) {
            for (Stmt stmtBeforeStmt : dominated) {
                if (containsWrite(stmtBeforeStmt))
                    return true;
            }
        }

        for (FlowEdge<BasicBlock> reverseEdge : dominated.getGraph().getReverseEdges(dominated)) {
            if (blockWritesBetween0(reverseEdge.src(), dominator, visitedBlocks, false)) {
                return true;
            }
        }

        return false;
    }
}
