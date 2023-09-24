package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heuristic;

import net.superblaubeere27.masxinlingvaj.analysis.CallGraph;
import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ParamExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLoadExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalRingAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ReachabilityAnalysis;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectLocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heap2reg.Heap2RegPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ArrayStoreStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutFieldStmt;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.Opcode.*;

public class InliningHeuristic {
    private static final int BASIC_BLOCK_COST = 3;
    private static final int SPECIAL_INLINE_DISCOUNT = 8;
    private static final int GENERAL_INLINE_DISCOUNT = 50;
    private static final int OUTSOURCE_BOUNTY = 100;
    private final MLVCompiler compiler;
    private final CallGraph callGraph;
    private final HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap;
    private final HashMap<CompilerMethod, MethodAnalysisInfo> analysisCache = new HashMap<>();
    /**
     * Which methods are currently being analyzed? Used to prevent a stack overflow when call graph looks like
     * A -> B -> C -> B -> C
     */
    private final HashSet<CompilerMethod> methodsAnalyzing = new HashSet<>();

    public InliningHeuristic(MLVCompiler compiler, CallGraph callGraph, HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap) {
        this.compiler = compiler;
        this.callGraph = callGraph;
        this.methodCfgMap = methodCfgMap;
    }

    private static int instructionCost(CodeUnit instruction) {
        int cost = 0;

        for (Expr child : instruction.children) {
            if (child != null)
                cost += instructionCost(child);
        }

        switch (instruction.getOpcode()) {
            case PHI_STORE, LOCAL_LOAD, PHI, GET_PARAM, LOCAL_STORE, RET_VOID, RET, CONST_NULL, CONST_INT, CONST_LONG, CONST_FLOAT, CONST_DOUBLE -> {
                return cost;
            }
            case UNCONDITIONAL_BRANCH, CONDITIONAL_BRANCH, SWITCH -> {
                return cost;
            }
            case CONST_STRING, CONST_TYPE, CATCH, OBJECT_COMPARE, ALLOC_OBJECT, ALLOC_ARRAY, MONITOR -> {
                return cost + 5;
            }
            case BINOP, NEGATION, PRIMITIVE_CAST, INTEGER_COMPARE, FLOAT_COMPARE -> {
                return cost + 1;
            }
            case ARRAY_STORE -> {
                return cost + (referencesParam(((ArrayStoreStmt) instruction).getArray(), true) ? -6 : 9);
            }
            case ARRAY_LOAD -> {
                return cost + (referencesParam(((ArrayLoadExpr) instruction).getArray(), true) ? -6 : 9);
            }
            case GET_STATIC_FIELD, PUT_STATIC_FIELD, INSTANCEOF, INVOKE_STATIC, INVOKE_INSTANCE -> {
                return cost + 7;
            }
            case PUT_FIELD -> {
                return cost + (referencesParam(((PutFieldStmt) instruction).getInstance(), false) ? -10 : 7);
            }
            case GET_FIELD -> {
                return cost + (referencesParam(((GetFieldExpr) instruction).getInstance(), false) ? -10 : 7);
            }
            case CHECKCAST -> {
                return cost + 6;
            }
            case EXPR -> {
                return cost;
            }
            case EXCEPTION_CHECK, CREATE_REF, DELETE_REF, CLEAR_EXCEPTION, ARRAY_LENGTH -> {
                return cost + 3;
            }
        }
        throw new IllegalStateException("Unexpected value: " + instruction.getOpcode());
    }

    static boolean referencesParam(Expr expr, boolean orFieldParam) {
        if (expr instanceof ParamExpr) {
            return true;
        } else if (expr instanceof VarExpr varExpr) {
            return referencesParam(expr.getBlock().getGraph().getLocals().defs.get(varExpr.getLocal()).getExpression(), orFieldParam);
        } else if (expr instanceof GetFieldExpr getFieldExpr && orFieldParam) {
            return referencesParam(getFieldExpr.getInstance(), false);
        }

        return false;
    }

    public static Assumption getAdditionalAssumptions(Assumption localInfo, Expr expr) {
        if (expr instanceof GetFieldExpr getFieldExpr) {
            if (referencesParam(getFieldExpr.getInstance(), false)) {
                var info = ((ObjectLocalInfo) localInfo);

                return (info == null ? ObjectLocalInfo.create() : info).assumeIsNull(false);
            }
        }

        return null;
    }

    /**
     * Does calling this expression on an object instance disqualify the object from being pulled into registers?
     */
    private static boolean isViableUsage(CodeUnit parent, boolean isReturnViable) {
        if (parent instanceof GetFieldExpr || parent instanceof PutFieldStmt)
            return true;

        if (parent instanceof PhiExpr)
            return true;

        return isReturnViable && parent instanceof RetStmt;
    }

    public void invalidateCache(CompilerMethod method) {
        this.analysisCache.remove(method);
    }

    private static int calculateSpecialInlineCost(int cost) {
        return Math.max(cost - SPECIAL_INLINE_DISCOUNT, -3);
    }

    /**
     * @return analysis info if available, if there is an analysis loop, it returns null
     */
    MethodAnalysisInfo retrieveAnalysisInfo(CompilerMethod method) {
        // Is there an analysis loop? If yes, prevent stack overflow
        if (this.methodsAnalyzing.contains(method)) {
            return null;
        }

        if (this.analysisCache.containsKey(method)) {
            return this.analysisCache.get(method);
        }

        MethodAnalysisInfo result = null;

        var cfg = this.methodCfgMap.get(method);

        if (cfg != null)
            result = this.analyzeMethod(cfg);

        this.analysisCache.put(method, result);

        return result;
    }

    private static int calculateCost(ControlFlowGraph cfg) {
        var cost = 0;

        for (BasicBlock entry : cfg.vertices()) {
            for (Stmt stmt : entry) {
                cost += instructionCost(stmt);
            }

            cost += BASIC_BLOCK_COST;
        }

        return cost;
    }

    private static int calculateCost(ControlFlowGraph cfg, LocalVariableAnalyzer analyzer) {
        ReachabilityAnalysis reachabilityAnalysis = new ReachabilityAnalysis(cfg, analyzer);

        var cost = 0;

        for (BasicBlock entry : cfg.vertices()) {
            if (!reachabilityAnalysis.isReachable(entry))
                continue;

            for (Stmt stmt : entry) {
                cost += instructionCost(stmt);
            }

            cost += BASIC_BLOCK_COST;
        }

        return cost;
    }

    /**
     * @param cfg         The method that is inlined <i>into</i>
     * @param targetCfg   The method that <i>is</i> inlined
     * @param invokeCount How often was the method invoked within cfg?
     */
    public boolean shouldInline(ControlFlowGraph cfg, ControlFlowGraph targetCfg, LocalVariableAnalyzer analyzer, int invokeCount) {
        if (cfg.size() > 150 && targetCfg.size() > 1) {
            return false;
        }

        var liftableRings = findLiftableRings(cfg, false);

        for (Map.Entry<LocalRingAnalyzer.LocalVariableRing, LiftabilityResult> liftableRing : liftableRings.entrySet()) {
            if (liftableRing.getValue().methodsRequiringInliningAndCosts.containsKey(targetCfg.getCompilerMethod())
                    && liftableRing.getValue().cost < OUTSOURCE_BOUNTY) {
                return true;
            }
        }

        var analysisInfo = retrieveAnalysisInfo(targetCfg.getCompilerMethod());

        var specialCost = calculateSpecialInlineCost(calculateCost(targetCfg, analyzer));

        System.out.println(targetCfg.getCompilerMethod().getIdentifier() + " A: " + analysisInfo.getGeneralInlineCost(invokeCount, specialCost) + "/" + analysisInfo.specialInlineCost);

        return analysisInfo.getGeneralInlineCost(invokeCount, specialCost) < GENERAL_INLINE_DISCOUNT;
    }

    private MethodAnalysisInfo analyzeMethod(ControlFlowGraph cfg) {
        this.methodsAnalyzing.add(cfg.getCompilerMethod());

        int cost = calculateCost(cfg);

        LiftabilityResult[] liftabilityResults;

        if (Arrays.stream(cfg.getArgumentTypes()).anyMatch(x -> x == ImmType.OBJECT)) {
            liftabilityResults = getParamLiftCosts(cfg);
        } else {
            liftabilityResults = new LiftabilityResult[cfg.getArgumentTypes().length];
        }

        this.methodsAnalyzing.remove(cfg.getCompilerMethod());

        int nEdges = 0;

        for (CallGraph.CallGraphMethodCall reverseEdge : callGraph.getReverseEdges(callGraph.getMethodVertex(cfg.getCompilerMethod().getIdentifier()))) {
            var cm = this.compiler.getIndex().getMethod(reverseEdge.src().getMethod());

            if (cm != null && cm.wasMarkedForCompilation())
                nEdges += 1;
        }

        return new MethodAnalysisInfo(liftabilityResults, nEdges, calculateSpecialInlineCost(cost));
    }

    private LiftabilityResult[] getParamLiftCosts(ControlFlowGraph cfg) {
        var liftableRings = findLiftableRings(cfg, true);
        LocalsPool locals = cfg.getLocals();

        List<ArrayList<LiftabilityResult>> results =
                IntStream
                        .range(0, cfg.getArgumentTypes().length)
                        .mapToObj(x -> new ArrayList<LiftabilityResult>()).collect(Collectors.toList());

        for (var liftableRing : liftableRings.entrySet()) {
            LiftabilityResult liftabilityResult = liftableRing.getValue();

            for (Local variable : liftableRing.getKey().getVariables()) {
                if (locals.defs.get(variable).getExpression() instanceof ParamExpr paramExpr) {
                    int paramIdx = paramExpr.getParamIdx();

                    if (liftabilityResult == null) {
                        results.set(paramIdx, null);
                    } else {
                        var list = results.get(paramIdx);

                        if (list != null)
                            list.add(liftabilityResult);
                    }
                }
            }
        }

        return results.stream().map(this::mergeLiftabilityResults).toArray(LiftabilityResult[]::new);
    }

    /**
     * Finds liftable rings (see {@link Heap2RegPass})
     *
     * @param isReturnViable Can the ret instruction be part of a local ring? Used when the method is planned to be
     *                       inlined (because ret turns into a simple assign stmt)
     * @return key: The liftable rings, value: The liftability result
     */
    private HashMap<LocalRingAnalyzer.LocalVariableRing, LiftabilityResult> findLiftableRings(ControlFlowGraph cfg, boolean isReturnViable) {
        LocalRingAnalyzer analyzer = LocalRingAnalyzer.buildLocalRing(cfg);

        HashMap<LocalRingAnalyzer.LocalVariableRing, LiftabilityResult> result = new HashMap<>();

        for (LocalRingAnalyzer.LocalVariableRing ring : analyzer.getRings()) {
            var liftabilityResult = isLiftable(cfg, ring, isReturnViable);

            if (liftabilityResult == null)
                continue;

            result.put(ring, liftabilityResult);
        }

        return result;
    }

    /**
     * @return If lifting is impossible null
     */
    private LiftabilityResult isLiftable(ControlFlowGraph cfg, LocalRingAnalyzer.LocalVariableRing ring, boolean isReturnViable) {
        LocalsPool locals = cfg.getLocals();

        ArrayList<LiftabilityResult> liftabilityResults = new ArrayList<>();

        String allocationType = null;

        for (Local variable : ring.getVariables()) {
            // Check if there are any instructions that reference the object that would disqualify it from being lifted
            // into registers
            for (VarExpr varExpr : locals.uses.getNonNull(variable)) {
                // Is the usage a method? If it is, check if it can be found and add it to the method list
                if (varExpr.getParent() instanceof InvokeExpr invokeExpr) {
                    LiftabilityResult liftabilityResult = findLiftResultsForMethod(invokeExpr, variable, true);

                    if (liftabilityResult == null)
                        return null;

                    liftabilityResults.add(liftabilityResult);

                    continue;
                }

                if (!isViableUsage(varExpr.getParent(), isReturnViable))
                    return null;
            }

            Expr declaringExpr = locals.defs.get(variable).getExpression();

            if (declaringExpr instanceof InvokeExpr invokeExpr) {
                LiftabilityResult liftabilityResult = findLiftResultsForMethod(invokeExpr, variable, true);

                if (liftabilityResult == null)
                    return null;

                liftabilityResults.add(liftabilityResult);

                continue;
            }

            if (declaringExpr instanceof ParamExpr)
                continue;

            // An object can only be lifted if it is allocated on the same function
            if (!(declaringExpr instanceof AllocObjectExpr))
                return null;

            String currentType = ((AllocObjectExpr) declaringExpr).getAllocatedType();

            // Does the type of class this expression allocates differ from the other expressions that were found
            if (allocationType != null && !currentType.equals(allocationType))
                return null;

            allocationType = currentType;
        }

        liftabilityResults.add(new LiftabilityResult(0, new HashMap<>(0), allocationType));

        return mergeLiftabilityResults(liftabilityResults);
    }

    private LiftabilityResult findLiftResultsForMethod(InvokeExpr invokeExpr, Local local, boolean addCurrentMethod) {
        CompilerMethod method = this.compiler.getIndex().getMethod(invokeExpr.getTarget());

        if (method == null)
            return null;

        List<Expr> children = invokeExpr.getChildren();

        MethodAnalysisInfo analysisInfo = retrieveAnalysisInfo(method);

        if (analysisInfo == null)
            return null;

        ArrayList<LiftabilityResult> results = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof VarExpr varExpr && varExpr.getLocal().equals(local))) {
                continue;
            }

            int cfgTargetIndex = i;

            if (invokeExpr instanceof InvokeInstanceExpr) {
                // Rotate right 1, due to the unfortunate ordering in InvokeInstanceExpr
                cfgTargetIndex = (i + 1) % children.size();
            }

            var paramLiftability = analysisInfo.paramLiftabilityResults[cfgTargetIndex];

            if (paramLiftability == null)
                return null;

            results.add(paramLiftability);
        }

        if (addCurrentMethod) {
            var a = new HashMap<CompilerMethod, Integer>();

            a.put(method, analysisInfo.specialInlineCost);

            results.add(new LiftabilityResult(analysisInfo.specialInlineCost, a, null));
        }

        return mergeLiftabilityResults(results);
    }

    private LiftabilityResult mergeLiftabilityResults(ArrayList<LiftabilityResult> results) {
        int totalCost = 0;
        String allocationType = null;

        for (LiftabilityResult result : results) {
            if (result.allocationType != null) {
                if (allocationType != null && !result.allocationType.equals(allocationType)) {
                    return null;
                }

                allocationType = result.allocationType;
            }

            totalCost += result.cost;
        }

        HashMap<CompilerMethod, Integer> costs = new HashMap<>();

        for (LiftabilityResult result : results) {
            for (var method : result.methodsRequiringInliningAndCosts.entrySet()) {
                costs.put(method.getKey(), costs.getOrDefault(method.getKey(), 0) + method.getValue());
            }
        }

        return new LiftabilityResult(totalCost, costs, allocationType);
    }

    private record LiftabilityResult(int cost, HashMap<CompilerMethod, Integer> methodsRequiringInliningAndCosts,
                                     String allocationType) {

    }

    /**
     * @param paramLiftabilityResults LiftabilityResults. index is null if impossible
     */
    private record MethodAnalysisInfo(
            LiftabilityResult[] paramLiftabilityResults,
            int nEdges,
            int specialInlineCost
    ) {
        public int getGeneralInlineCost(int additionalInvokes) {
            return this.getGeneralInlineCost(additionalInvokes, this.specialInlineCost);
        }

        public int getGeneralInlineCost(int additionalInvokes, int specialCost) {
            return Math.max(0, specialCost * (this.nEdges + additionalInvokes - 2));
        }
    }
}
