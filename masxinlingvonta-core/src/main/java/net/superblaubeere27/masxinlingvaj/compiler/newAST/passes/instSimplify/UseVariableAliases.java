package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.LT79Dom;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.FloatingPointCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.AllocArrayExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalRingAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.NumberRelation;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.VariableRelationObject;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.reuseLocals.ReuseLocalsPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;

import java.util.Collections;
import java.util.Comparator;

public class UseVariableAliases extends Pass {
    /**
     * Finds out how worthy a local ring is. Worthiness means low entropy. Thus, this heuristic aims to calculate how
     * good we know about the content of the variable.
     */
    private static RingEstimation getEstimationOf(ControlFlowGraph cfg, Local local, LocalRingAnalyzer.LocalVariableRing ring) {
        int firstClass = 0;
        int secondClass = 0;
        int thirdClass = 0;

        for (Local variable : ring.getVariables()) {
            var expr = cfg.getLocals().defs.get(variable).getExpression();

            if (expr instanceof ArrayLengthExpr) {
                secondClass++;
            } else if (expr instanceof ParamExpr) {
                firstClass++;
            } else if (expr instanceof IntegerArithmeticsExpr || expr instanceof FloatingPointArithmeticsExpr || expr instanceof NegationExpr || expr instanceof IntegerCompareExpr || expr instanceof FloatingPointCompareExpr) {
                secondClass++;
            } else if (expr instanceof VarExpr || expr instanceof PhiExpr) {
                // Do nothing as it is already covered by the ring analyzer
            } else if (expr instanceof CatchExpr) {
                secondClass++;
            } else if (expr instanceof AllocObjectExpr || expr instanceof AllocArrayExpr) {
                firstClass++;
            } else if (expr instanceof ObjectCompareExpr) {
                secondClass++;
            } else if (expr instanceof ConstExpr) {
                firstClass++;
            } else {
                thirdClass++;
            }
        }

        return new RingEstimation(local, firstClass, secondClass, thirdClass);
    }

    public static Expr remap(UseVariableAliasesData data, LocalInfoSnapshot snapshot, Expr expr) {
        if (!(expr instanceof VarExpr varExpr)) {
            return null;
        }

        var localInfo = snapshot.getLocalAssumption(varExpr.getLocal());

        // Find locals which we know contain the same value as this variable
        var possibleAliases = AssumptionAnalyzer.extractActualValues(localInfo, assumption -> {
            if (assumption instanceof NumberRelation<?> numberRelation) {
                if (numberRelation.getOperator() == IntegerCompareExpr.Operator.EQUAL && numberRelation.getRhs() instanceof VariableRelationObject<?> variableRelationObject) {
                    var defStmt = expr.getBlock().getGraph().getLocals().defs.get(variableRelationObject.getVariable());

                    if (!ReuseLocalsPass.isStatementBetween(expr.getRootParent(), variableRelationObject.getDeclaringStatement(), x -> x == defStmt)) {
                        return Collections.singleton(variableRelationObject.getVariable());
                    }
                }
            }

            return Collections.emptySet();
        });

        if (possibleAliases.isEmpty()) {
            return null;
        }

        var cfg = expr.getBlock().cfg;
        var currentRing = getEstimationOf(cfg, varExpr.getLocal(), data.getRingAnalyzer().getRingOf(varExpr.getLocal()));

        // Find the worthiest local which contains the same value
        var bestRing = possibleAliases.stream().map(local -> {
            // Find statements (=> locals) which contribute to the given local.
            return getEstimationOf(cfg, local, data.getRingAnalyzer().getRingOf(local));
        }).max(RingEstimation::compareTo);

        if (bestRing.isEmpty() || currentRing.compareTo(bestRing.get()) >= 0) {
            return null;
        }

        return new VarExpr(bestRing.get().local());
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        var analyzer = new LocalVariableAnalyzer(cfg);

        analyzer.analyze();

        var data = new UseVariableAliasesData(cfg, analyzer);

        StatementTransaction transaction = new StatementTransaction();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                var snapshot = data.analyzer.getStatementSnapshot(stmt);

                stmt.remapChildren(transaction, expr -> remap(data, snapshot, expr));
            }
        }

        transaction.apply();
    }

    private record RingEstimation(
            Local local, int firstClass,
            int secondClass,
            int thirdClass
    ) implements Comparable<RingEstimation> {
        public static final Comparator<RingEstimation> COMPARATOR = Comparator
                .<RingEstimation>comparingInt(x -> -(x.firstClass + (x.secondClass + x.thirdClass) * 2))
                .thenComparingInt(x -> -x.thirdClass)
                .thenComparingInt(x -> -x.secondClass)
                .thenComparingInt(x -> -x.firstClass);

        @Override
        public int compareTo(RingEstimation other) {
            return COMPARATOR.compare(this, other);
        }
    }

    public static class UseVariableAliasesData {
        private final ControlFlowGraph cfg;
        private final LocalVariableAnalyzer analyzer;
        private LT79Dom<BasicBlock, FlowEdge<BasicBlock>> domTree;
        private LocalRingAnalyzer ringAnalyzer;

        public UseVariableAliasesData(ControlFlowGraph cfg, LocalVariableAnalyzer analyzer) {
            this.cfg = cfg;
            this.analyzer = analyzer;


            this.analyzer.analyze();
        }

        public LT79Dom<BasicBlock, FlowEdge<BasicBlock>> getDomTree() {
            if (this.domTree == null) {
                this.domTree = new LT79Dom<>(this.cfg, this.cfg.getEntry());
            }

            return domTree;
        }

        public LocalRingAnalyzer getRingAnalyzer() {
            if (this.ringAnalyzer == null) {
                this.ringAnalyzer = LocalRingAnalyzer.buildLocalRing(this.cfg);
            }

            return ringAnalyzer;
        }
    }
}
