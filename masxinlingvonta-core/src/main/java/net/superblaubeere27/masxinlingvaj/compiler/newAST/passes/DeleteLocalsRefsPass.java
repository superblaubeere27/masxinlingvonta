package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.SSABlockLivenessAnalyser;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.CreateLocalRefExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionPredicates;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.SwitchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.DeleteRefStmt;

import java.util.*;
import java.util.stream.Collectors;

public class DeleteLocalsRefsPass extends Pass {

    private static void step1(ControlFlowGraph cfg, HashMap<FlowEdge<BasicBlock>, Set<Local>> phiUsageMap) {
        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                if (stmt instanceof CopyPhiStmt) {
                    PhiExpr phiExpr = ((CopyPhiStmt) stmt).getExpression();

                    for (Map.Entry<BasicBlock, Expr> argument : phiExpr.getArguments().entrySet()) {
                        if (argument.getValue() instanceof VarExpr) {
                            Local local = ((VarExpr) argument.getValue()).getLocal();

                            phiUsageMap.computeIfAbsent(new BasicFlowEdge(argument.getKey(), vertex), e -> new HashSet<>()).add(local);
                        }
                    }
                } else {
                    // Phi statements can only occur at the beginning of the block
                    break;
                }
            }
        }
    }

    private static boolean isUsedByPhi(BasicBlock block, Local local) {
        for (Stmt stmt : block) {
            if (!(stmt instanceof CopyPhiStmt))
                break;

            if (((CopyPhiStmt) stmt).getExpression().getArguments().values().stream().anyMatch(x -> x instanceof VarExpr && ((VarExpr) x).getLocal().equals(local)))
                return true;
        }

        return false;
    }

    private static Local getReturnedVariableOfBlockOrNull(BasicBlock vertex) {
        var terminator = vertex.getTerminator();

        if (!(terminator instanceof RetStmt retStmt)) {
            return null;
        }

        var retValue = retStmt.getValue();

        if (!(retValue instanceof VarExpr returnedVarExpr)) {
            return null;
        }

        return returnedVarExpr.getLocal();
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        var livenessAnalyser = new SSABlockLivenessAnalyser(cfg);

        livenessAnalyser.compute();

        var localAnalyzer = new LocalVariableAnalyzer(cfg);

        localAnalyzer.analyze();

        HashMap<FlowEdge<BasicBlock>, Set<Local>> phiUsageMap = new HashMap<>();

        step1(cfg, phiUsageMap);

        var deletes = new HashMap<FlowEdge<BasicBlock>, ArrayList<Local>>();
        var copies = new HashMap<FlowEdge<BasicBlock>, ArrayList<Local>>();

        for (BasicBlock vertex : cfg.verticesInOrder()) {
            var outVars = livenessAnalyser.out(vertex);

            for (Local outVar : outVars) {
                if (outVar.getType() != ImmType.OBJECT) {
                    continue;
                }

                for (FlowEdge<BasicBlock> edge : cfg.getEdges(vertex)) {
                    var inVars = livenessAnalyser.in(edge.dst());
                    var phiUsage = phiUsageMap.getOrDefault(edge, Collections.emptySet());

                    // Can the variable be moved? Otherwise, create a copy
                    var mustCopy = phiUsage.contains(outVar) && inVars.contains(outVar);
                    var canBeKilled = !inVars.contains(outVar) && !phiUsage.contains(outVar);

                    if (mustCopy) {
                        copies.computeIfAbsent(edge, e -> new ArrayList<>(outVars.size())).add(outVar);
                    } else if (canBeKilled) {
                        deletes.computeIfAbsent(edge, e -> new ArrayList<>(outVars.size())).add(outVar);
                    }
                }
            }

            // If there is a ret %0, remember %0, so we don't delete it
            var returnedVariable = getReturnedVariableOfBlockOrNull(vertex);

            List<Local> variablesToKill = new ArrayList<>();

            var inVars = new HashSet<>(livenessAnalyser.in(vertex));

            inVars.addAll(vertex.getDefinedVariables());

            for (Local inVar : inVars) {
                if (inVar.getType() != ImmType.OBJECT)
                    continue;

                var isStillUsed = outVars.contains(inVar) || inVar.equals(returnedVariable) || isUsedByPhi(vertex, inVar);

                if (isStillUsed)
                    continue;

                variablesToKill.add(inVar);
            }

            boolean shallExtractCondition = false;

            for (Expr child : vertex.getTerminator().enumerateOnlyChildren()) {
                if (child instanceof VarExpr && variablesToKill.contains(((VarExpr) child).getLocal())) {
                    shallExtractCondition = true;
                    break;
                }
            }

            // Create delete statements for all variables that are not known to be null
            List<Stmt> stmtsToAdd = variablesToKill.stream()
                    .filter(x -> !localAnalyzer.getStatementSnapshot(vertex.getTerminator()).getLocalInfo(x).extractValue(AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(false))
                    .map(x -> new DeleteRefStmt(new VarExpr(x)))
                    .collect(Collectors.toList());

            if (shallExtractCondition) {
                var terminator = vertex.getTerminator();

                VarExpr local;
                Expr operand;

                if (terminator.getOpcode() == Opcode.CONDITIONAL_BRANCH) {
                    local = new VarExpr(cfg.getLocals().allocStatic(ImmType.BOOL));
                    operand = ((ConditionalBranch) terminator).getCond();
                } else if (terminator.getOpcode() == Opcode.SWITCH) {
                    local = new VarExpr(cfg.getLocals().allocStatic(ImmType.INT));
                    operand = ((SwitchStmt) terminator).getOperand();
                } else {
                    throw new UnsupportedOperationException();
                }

                cfg.writeAt(terminator, operand, local);

                stmtsToAdd.add(0, new CopyVarStmt(local.copy(), operand));
            }

            vertex.addAll(vertex.size() - 1, stmtsToAdd);
        }

        for (Map.Entry<FlowEdge<BasicBlock>, ArrayList<Local>> scheduledChange : copies.entrySet()) {
            if (scheduledChange.getValue().isEmpty())
                continue;

            var newBlock = insertCopies(cfg, scheduledChange.getKey(), scheduledChange.getValue());

            var deleteParams = deletes.get(scheduledChange.getKey());

            if (deleteParams != null) {
                deletes.remove(scheduledChange.getKey());
                deletes.put(new BasicFlowEdge(scheduledChange.getKey().src(), newBlock), deleteParams);
            }
        }

        for (Map.Entry<FlowEdge<BasicBlock>, ArrayList<Local>> scheduledChange : deletes.entrySet()) {
            if (scheduledChange.getValue().isEmpty())
                continue;

            insertDeletes(cfg, scheduledChange.getKey(), scheduledChange.getValue(), localAnalyzer);
        }
    }

    private BasicBlock insertCopies(ControlFlowGraph cfg, FlowEdge<BasicBlock> edge, ArrayList<Local> killedVars) {
        BasicBlock newBlock = new BasicBlock(cfg);

        var varReplacements = new HashMap<Local, Local>(killedVars.size());

        for (Local killedVar : killedVars) {
            var replacement = cfg.getLocals().allocStatic(ImmType.OBJECT);

            newBlock.add(new CopyVarStmt(new VarExpr(replacement), new CreateLocalRefExpr(new VarExpr(killedVar))));

            varReplacements.put(killedVar, replacement);
        }

        newBlock.add(new UnconditionalBranch(edge.dst()));

        BranchStmt branch = (BranchStmt) edge.src().getTerminator();

        if (branch.replaceBasicBlock(edge.dst(), newBlock) == 0) {
            throw new IllegalStateException();
        }

        fixPhiParams(edge.dst(), edge.src(), newBlock, varReplacements);

        return newBlock;
    }

    private void insertDeletes(ControlFlowGraph cfg, FlowEdge<BasicBlock> edge, ArrayList<Local> killedVars, LocalVariableAnalyzer localAnalyzer) {
        BasicBlock newBlock = new BasicBlock(cfg);

        for (Local killedVar : killedVars) {
            // Don't kill a null value
            if (localAnalyzer.getBlockSnapshot(edge.dst()).getLocalInfo(killedVar).extractValue(AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(false)) {
                continue;
            }

            newBlock.add(new DeleteRefStmt(new VarExpr(killedVar)));
        }

        newBlock.add(new UnconditionalBranch(edge.dst()));

        BranchStmt branch = (BranchStmt) edge.src().getTerminator();

        if (branch.replaceBasicBlock(edge.dst(), newBlock) == 0) {
            throw new IllegalStateException();
        }

        fixPhiParams(edge.dst(), edge.src(), newBlock, null);
    }

    private void fixPhiParams(BasicBlock target, BasicBlock from, BasicBlock to, HashMap<Local, Local> variableReplacements) {
        for (Stmt stmt : target) {
            if (stmt instanceof CopyPhiStmt) {
                PhiExpr phi = ((CopyPhiStmt) stmt).getExpression();

                var arg = phi.getArgument(from);

                if (arg == null)
                    throw new IllegalStateException();

                if (variableReplacements != null && arg instanceof VarExpr) {
                    var replacement = variableReplacements.get(((VarExpr) arg).getLocal());

                    if (replacement != null) {
                        ((VarExpr) arg).setLocal(replacement);
                    }
                }

                phi.removeArgument(from);
                phi.setArgument(to, arg);
            } else {
                break;
            }
        }
    }

}
