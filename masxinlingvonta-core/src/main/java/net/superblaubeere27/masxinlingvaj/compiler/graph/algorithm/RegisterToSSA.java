package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;

import java.util.*;

public class RegisterToSSA {
    private final ControlFlowGraph cfg;
    private final LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dominanceCalculator;
    private final Liveness<BasicBlock> liveness;

    public RegisterToSSA(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.dominanceCalculator = new LT79Dom<>(cfg, cfg.getEntry());

        var liveness = new SSABlockLivenessAnalyser(cfg);

        liveness.compute();

        this.liveness = liveness;
    }

    private static Set<Local> getReferencedLocals(Expr expr) {
        HashSet<Local> locals = new HashSet<>();

        for (Expr child : expr.enumerateWithSelf()) {
            if (child instanceof VarExpr)
                locals.add(((VarExpr) child).getLocal());
        }

        return locals;
    }

    private void insertPhis() {
        HashMap<Local, List<BasicBlock>> defsites = new HashMap<>();

        for (BasicBlock basicBlock : this.cfg.vertices()) {
            for (Local local : basicBlock.getDefinedVariables()) {
                defsites.computeIfAbsent(local, e -> new ArrayList<>()).add(basicBlock);
            }
        }

        HashMap<BasicBlock, HashSet<Local>> definedPhis = new HashMap<>();

        for (Map.Entry<Local, List<BasicBlock>> variableArrayListEntry : defsites.entrySet()) {
            Local a = variableArrayListEntry.getKey();
            HashSet<BasicBlock> w = new HashSet<>(variableArrayListEntry.getValue());

            if (a.isSSA())
                continue;

            while (!w.isEmpty()) {
                BasicBlock n = w.iterator().next();

                w.remove(n);

                for (BasicBlock y : this.dominanceCalculator.getIteratedDominanceFrontier(n)) {
                    var aPhiY = definedPhis.computeIfAbsent(y, e -> new HashSet<>());

                    if (!aPhiY.contains(a) && liveness.in(y).contains(a)) {
                        insertPhi(y, a);

                        aPhiY.add(a);

                        if (!y.getDefinedVariables().contains(a)) {
                            w.add(y);
                        }
                    }
                }
            }
        }
    }

    private void insertPhi(BasicBlock basicBlock, Local variable) {
        HashMap<BasicBlock, Expr> args = new HashMap<>();

        this.cfg.getPredecessors(basicBlock).forEach(x -> args.put(x, new VarExpr(variable)));

        basicBlock.add(0, new CopyPhiStmt(new VarExpr(variable), new PhiExpr(args, variable.getType())));
    }

    private void rename() {
        HashMap<Local, ArrayDeque<StaticLocal>> stack = new HashMap<>();

        for (BasicBlock basicBlock : this.cfg.vertices()) {
            for (Local variable : basicBlock.getReferencedLocals()) {
                if (variable.isSSA())
                    continue;

                stack.computeIfAbsent(variable, e -> new ArrayDeque<>(Collections.singletonList(cfg.getLocals().allocStatic(variable.getType()))));
            }
        }

        var domTree = this.dominanceCalculator.getDominatorTree();

        rename(this.cfg.getEntry(), domTree, stack);
    }

    private void rename(BasicBlock n, DominatorTree<BasicBlock> domTree, HashMap<Local, ArrayDeque<StaticLocal>> stack) {
        Collection<Local> definedVars = n.enumerateDefinedVariables();

        renameBasicBlock(n, stack);

        this.cfg.getSuccessors(n).forEach(y -> {
            for (Stmt stmt : y) {
                for (Expr expr : stmt.enumerateOnlyChildren()) {
                    if (expr instanceof PhiExpr) {
                        Expr blockArgument = ((PhiExpr) expr).getArgument(n);

                        for (Local referencedLocal : getReferencedLocals(blockArgument)) {
                            if (referencedLocal.isSSA())
                                continue;

                            StaticLocal replacement = stack.get(referencedLocal).peekLast();

                            replaceUses(blockArgument, referencedLocal, replacement);
                        }

                    }

                }
            }
        });

        domTree.getSuccessors(n).forEach(x -> this.rename(x, domTree, stack));

        for (Local definedVar : definedVars) {
            if (definedVar.isSSA())
                continue;

            stack.get(definedVar).removeLast();
        }
    }

    private void replaceUses(Expr expr, Local target, Local replacement) {
        for (Expr child : expr.enumerateWithSelf()) {
            if (child instanceof VarExpr && ((VarExpr) child).getLocal().equals(target))
                ((VarExpr) child).setLocal(replacement);
        }
    }

    private void renameBasicBlock(BasicBlock basicBlock, HashMap<Local, ArrayDeque<StaticLocal>> stack) {
        for (Stmt statement : basicBlock) {
            for (Expr child : statement.getChildren()) {
                if (child instanceof PhiExpr)
                    continue;

                for (Local referencedLocal : getReferencedLocals(child)) {
                    if (referencedLocal.isSSA())
                        continue;

                    replaceUses(child, referencedLocal, stack.get(referencedLocal).peekLast());
                }
            }

            if (!(statement instanceof AbstractCopyStmt)) {
                continue;
            }

            Local definedVariable = ((AbstractCopyStmt) statement).getVariable().getLocal();

            if (definedVariable.isSSA())
                continue;

            StaticLocal loc = this.cfg.getLocals().allocStatic(definedVariable.getType());

            stack.get(definedVariable).addLast(loc);

            ((AbstractCopyStmt) statement).setVariable(new VarExpr(loc));
        }
    }

    public void process() {
        this.insertPhis();

        this.rename();
    }
}