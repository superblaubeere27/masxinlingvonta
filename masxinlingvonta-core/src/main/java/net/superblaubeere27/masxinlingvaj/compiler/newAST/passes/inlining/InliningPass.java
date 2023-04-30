package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining;

import net.superblaubeere27.masxinlingvaj.analysis.CallGraph;
import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ParamExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heuristic.InliningHeuristic;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode.LOCAL_LOAD;

public class InliningPass extends Pass {
//    private static final int INLINE_COST = 100;

    private final MLVCompiler compiler;
    private final CallGraph callGraph;
    private final HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap;
    private final InliningHeuristic heuristic;

    public InliningPass(MLVCompiler compiler, CallGraph callGraph, HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap) {
        this.heuristic = new InliningHeuristic(compiler, callGraph, methodCfgMap);

        this.compiler = compiler;
        this.callGraph = callGraph;
        this.methodCfgMap = methodCfgMap;
    }


    @Override
    public void apply(ControlFlowGraph cfg) {
        runSingleInlinePass(cfg);
    }

    public boolean runSingleInlinePass(ControlFlowGraph cfg) {
        ArrayList<InvokeExpr> invokesOfInterest = findInvokesOfInterest(cfg);

        for (InvokeExpr invokeExpr : invokesOfInterest) {
            var target = this.compiler.getIndex().getMethod(invokeExpr.getTarget());
            var targetCfg = this.methodCfgMap.get(target);

            System.out.println("Inlining " + target.getIdentifier() + " into " + cfg.getCompilerMethod().getIdentifier());

            VarExpr retValueLocal;

            var stmt = invokeExpr.getRootParent();

            if (stmt instanceof CopyVarStmt) {
                retValueLocal = ((CopyVarStmt) stmt).getVariable().copy();
            } else if (stmt instanceof ExpressionStmt) {
                retValueLocal = null;
            } else {
                throw new IllegalStateException();
            }
            var entryBB = stmt.getBlock();
            var exitBB = new BasicBlock(cfg);

            // Change the incoming parameter key from entry to exit bb
            refactorPhis(entryBB, exitBB);

            var invokeIdx = entryBB.indexOf(stmt);

            entryBB.remove(invokeIdx);

            while (entryBB.size() > invokeIdx) {
                exitBB.add(entryBB.remove(invokeIdx));
            }

            HashMap<BasicBlock, BasicBlock> basicBlockMap = new HashMap<>();

            basicBlockMap.put(targetCfg.getEntry(), entryBB);

            for (BasicBlock bb : targetCfg.vertices()) {
                basicBlockMap.computeIfAbsent(bb, block -> new BasicBlock(cfg));
            }

            HashMap<Local, Local> localMap = new HashMap<>();

            for (Local local : targetCfg.getLocals().defs.keySet()) {
                localMap.put(local, cfg.getLocals().allocStatic(local.getType()));
            }

            Expr[] params;

            if (invokeExpr instanceof InvokeInstanceExpr) {
                params = new Expr[invokeExpr.getArgTypes().length + 1];

                params[0] = invokeExpr.getChildren().get(invokeExpr.getChildren().size() - 1).copy();

                for (int i = 0; i < params.length - 1; i++) {
                    params[1 + i] = invokeExpr.getChildren().get(i).copy();
                }
            } else {
                params = invokeExpr.getChildren().stream().map(Expr::copy).toArray(Expr[]::new);
            }

            var entryBlock = basicBlockMap.get(targetCfg.getEntry());

            for (int i = 0; i < params.length; i++) {
                Expr param = params[i];

                var allocatedVar = new VarExpr(cfg.getLocals().allocStatic(param.getType()));

                entryBlock.add(new CopyVarStmt(allocatedVar, params[i]));

                params[i] = allocatedVar.copy();
            }

            PhiExpr exitPhi = null;

            if (invokeExpr.getReturnType().getSort() != Type.VOID) {
                exitPhi = new PhiExpr(new HashMap<>(), ImmType.fromJVMType(invokeExpr.getReturnType()));
            }

            PhiExpr finalExitPhi = exitPhi;

            basicBlockMap.forEach((oldBlock, newBlock) -> {
                for (Stmt oldStmt : oldBlock) {
                    var copiedStmt = oldStmt.copy();

                    if (copiedStmt instanceof BranchStmt) {
                        ((BranchStmt) copiedStmt).refactor(basicBlockMap);
                    } else if (copiedStmt instanceof AbstractCopyStmt) {
                        ((AbstractCopyStmt) copiedStmt).getVariable().refactorLocals(localMap);

                        if (copiedStmt instanceof CopyPhiStmt) {
                            ((CopyPhiStmt) copiedStmt).getExpression().refactorBasicBlocks(basicBlockMap);
                        }
                    }

                    for (Expr childExpr : copiedStmt.enumerateOnlyChildren()) {
                        if (childExpr instanceof VarExpr) {
                            ((VarExpr) childExpr).refactorLocals(localMap);
                        } else if (childExpr instanceof ParamExpr) {
                            CodeUnit parent = childExpr.getParent();

                            parent.writeAt(params[((ParamExpr) childExpr).getParamIdx()].copy(), parent.indexOf(childExpr));
                        }
                    }

                    if (copiedStmt instanceof RetStmt) {
                        var retVal = ((RetStmt) copiedStmt).getValue();

                        if (retVal.getOpcode() != LOCAL_LOAD) {
                            var alloc = cfg.getLocals().allocStatic(retVal.getType());

                            VarExpr retValVar = new VarExpr(alloc);

                            newBlock.add(new CopyVarStmt(retValVar, retVal.copy()));

                            retVal = retValVar;
                        } else {
                            retVal = retVal.copy();
                        }

                        finalExitPhi.setArgument(newBlock, retVal);
                        newBlock.add(new UnconditionalBranch(exitBB));
                    } else if (copiedStmt instanceof RetVoidStmt) {
                        newBlock.add(new UnconditionalBranch(exitBB));
                    } else {
                        newBlock.add(copiedStmt);
                    }
                }
            });

            if (retValueLocal != null) {
                exitBB.add(0, new CopyPhiStmt(retValueLocal, finalExitPhi));
            }
        }

        this.heuristic.invalidateCache(cfg.getCompilerMethod());

        return !invokesOfInterest.isEmpty();
    }

    /**
     * Finds all invokes that we are interested in (aka all <code>invokespecial</code>-exprs)
     */
    private ArrayList<InvokeExpr> findInvokesOfInterest(ControlFlowGraph cfg) {
        var invokesOfInterest = new ArrayList<InvokeExpr>();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                for (Expr child : stmt.enumerateOnlyChildren()) {
                    if (!(child instanceof InvokeExpr invokeExpr))
                        continue;

                    var target = this.compiler.getIndex().getMethod(invokeExpr.getTarget());

                    // We don't want to inline the method into itself on recursion
                    if (cfg.getCompilerMethod() == target)
                        continue;

                    var targetCfg = this.methodCfgMap.get(target);

                    if (targetCfg == null)
                        continue;

                    if (!heuristic.shouldInline(cfg, targetCfg))
                        continue;

                    if (invokeExpr instanceof InvokeInstanceExpr && ((InvokeInstanceExpr) invokeExpr).getInvokeType() != InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL)
                        continue;

                    invokesOfInterest.add(invokeExpr);
                }
            }
        }

        return invokesOfInterest;
    }

    private void refactorPhis(BasicBlock entryBB, BasicBlock exitBB) {
        var entryPhiRefactorList = new HashMap<BasicBlock, BasicBlock>();

        entryPhiRefactorList.put(entryBB, exitBB);

        for (FlowEdge<BasicBlock> edge : entryBB.getGraph().getEdges(entryBB)) {
            for (Stmt possiblePhiStmt : edge.dst()) {
                if (!(possiblePhiStmt instanceof CopyPhiStmt))
                    break;

                var phi = ((CopyPhiStmt) possiblePhiStmt).getExpression();

                phi.refactorBasicBlocks(entryPhiRefactorList);
            }
        }
    }

}
