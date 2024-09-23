package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ParamExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.LoadFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.AllocArrayExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLoadExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ThrowsProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.PrimitiveAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.conditionalAssumptions.ConditionalAssumptionByExceptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.conditionalAssumptions.ConditionalAssumptionByNumberValue;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.functionAssumptions.InstrinsicAssumptions;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.NullStateAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.ExpressionSimplifier;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ArrayStoreStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ClearExceptionStateStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.MonitorStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ThrowStmt;
import net.superblaubeere27.masxinlingvaj.utils.Pair;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption.ObjectTypeRelation.IS_INSTANCE_OF;
import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption.assumeClassIsExactly;
import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption.assumeInstanceOf;


public class LocalVariableAnalyzer {
    private static final int MAX_BLOCKS = 200;

    public final ExpressionSimplifier simplifier;
    public final HashMap<Integer, AtomicInteger> blockCounts = new HashMap<>();
    final ControlFlowGraph cfg;
    final HashMap<Stmt, LocalInfoSnapshot> snapshots = new HashMap<>();
    /**
     * Snapshots that represents the state in the beginning of the block
     */
    final HashMap<BasicBlock, LocalInfoSnapshot> basicBlockSnapshots = new HashMap<>();
    private final Assumption[] paramSnapshots;
    private final BiFunction<Assumption, Expr, Assumption> baseLocalInfoFactory;
    public int counter = 0;

    public LocalVariableAnalyzer(ControlFlowGraph cfg) {
        this(cfg, null, new Assumption[cfg.getArgumentTypes().length], null);
    }

    public LocalVariableAnalyzer(ControlFlowGraph cfg, ExpressionSimplifier simplifier, Assumption[] paramSnapshots, BiFunction<Assumption, Expr, Assumption> baseLocalInfoFactory) {
        this.cfg = cfg;
        this.simplifier = simplifier;
        this.paramSnapshots = paramSnapshots;
        this.baseLocalInfoFactory = baseLocalInfoFactory;
    }

    /**
     * Returns the blocks that a branch can jump to, combined with the assumptions that can be drawn from the specific jump
     */
    static Pair<BasicBlock, LocalInfoSnapshot>[] getSuccessiveBlockSnapshots(LocalInfoSnapshot currentSnapshot, BranchStmt branchStmt) {
        if (branchStmt instanceof ConditionalBranch condBranch) {
            var assumptionsOfTrueAndFalseValue = getAssumptionsOfTrueAndFalseValue(currentSnapshot, condBranch.getCond());

            return new Pair[]{new Pair<>(condBranch.getIfTarget(), assumptionsOfTrueAndFalseValue.getFirst()), new Pair<>(condBranch.getElseTarget(), assumptionsOfTrueAndFalseValue.getSecond())};
        } else if (branchStmt instanceof ExceptionCheckStmt exceptionCheckStmt) {
            var okSnapshot = currentSnapshot.copy();
            var exceptionSnapshot = currentSnapshot.copy();

            okSnapshot.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(false));
            exceptionSnapshot.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(true));

            for (Map.Entry<Local, Assumption> localAssumptionEntry : okSnapshot.getLocalAssumptions().entrySet()) {
                var currentAssumption = localAssumptionEntry.getValue();

                var okAssumptions = AssumptionAnalyzer.extractAssumption(currentAssumption, assumption -> {
                    if (assumption instanceof ConditionalAssumptionByExceptionState derivedFromExceptionState) {
                        return !derivedFromExceptionState.getExceptionState() ? derivedFromExceptionState.getThenAssumption() : Assumption.NoAssumption.INSTANCE;
                    }

                    return assumption;
                });

                localAssumptionEntry.setValue(okAssumptions);
            }
            for (Map.Entry<Local, Assumption> localAssumptionEntry : exceptionSnapshot.getLocalAssumptions().entrySet()) {
                var currentAssumption = localAssumptionEntry.getValue();

                var failAssumption = AssumptionAnalyzer.extractAssumption(currentAssumption, assumption -> {
                    if (assumption instanceof ConditionalAssumptionByExceptionState derivedFromExceptionState) {
                        return derivedFromExceptionState.getExceptionState() ? derivedFromExceptionState.getThenAssumption() : Assumption.NoAssumption.INSTANCE;
                    }

                    return assumption;
                });

                localAssumptionEntry.setValue(failAssumption);
            }

            return new Pair[]{new Pair<>(exceptionCheckStmt.getOkTarget(), okSnapshot), new Pair<>(exceptionCheckStmt.getExceptionTarget(), exceptionSnapshot)};
        }

        return Arrays.stream(branchStmt.getNextBasicBlocks()).map(x -> new Pair<>(x, currentSnapshot)).toArray(Pair[]::new);
    }

    /**
     * Returns the assumptions that are associated with taking the given target of the branch
     */
    public static LocalInfoSnapshot getSuccessiveBlockSnapshot(LocalInfoSnapshot currentSnapshot, BranchStmt branchStmt, int branchIndex) {
        return getSuccessiveBlockSnapshots(currentSnapshot, branchStmt)[branchIndex].getSecond();
    }

    /**
     * Basically tells what can be assumed if bool expression results in a true or false value
     */
    private static Pair<LocalInfoSnapshot, LocalInfoSnapshot> getAssumptionsOfTrueAndFalseValue(LocalInfoSnapshot current, Expr expr) {
        if (expr instanceof ObjectCompareExpr compareExpr) {
            VarExpr comparedVar;
            Expr otherExpr;

            if (compareExpr.getLhs() instanceof VarExpr) {
                comparedVar = (VarExpr) compareExpr.getLhs();
                otherExpr = compareExpr.getRhs();
            } else if (compareExpr.getRhs() instanceof VarExpr) {
                comparedVar = (VarExpr) compareExpr.getRhs();
                otherExpr = compareExpr.getLhs();
            } else {
                return new Pair<>(current, current);
            }

            var ifEqualSnapshot = current.copy();
            var ifNotEqualSnapshot = current.copy();

            var ifEquals = ifEqualSnapshot.getOrCreateLocalAssumption(comparedVar.getLocal());
            var ifNotEquals = ifNotEqualSnapshot.getOrCreateLocalAssumption(comparedVar.getLocal());

            if (otherExpr instanceof ConstNullExpr) {
                ifEquals = LinkedAssumptions.and(ifEquals, NullStateAssumption.IS_NULL);
                ifNotEquals = LinkedAssumptions.and(ifNotEquals, NullStateAssumption.IS_NON_NULL);
            }

            ifEqualSnapshot.putLocalAssumption(comparedVar.getLocal(), ifEquals);
            ifNotEqualSnapshot.putLocalAssumption(comparedVar.getLocal(), ifNotEquals);

            return new Pair<>(ifEqualSnapshot, ifNotEqualSnapshot);
        } else if (expr instanceof IntegerCompareExpr integerCompareExpr && integerCompareExpr.getOperator() == IntegerCompareExpr.Operator.EQUAL && integerCompareExpr.getLhs() instanceof VarExpr varExpr && integerCompareExpr.getRhs() instanceof ConstIntExpr constIntExpr) {
            var value = constIntExpr.getValue();

            var localInfo = current.getLocalAssumption(varExpr.getLocal());

            // TODO: This mostly sucks since we cannot use assumption specific functions for handling special cases.
            var ifValues = AssumptionAnalyzer.extractActualValues(localInfo, assumption -> {
                if (assumption instanceof ConditionalAssumptionByNumberValue<?> derived && derived.getRelation().getRhs() instanceof ConstantRelationObject<?> constantRelationObject && constantRelationObject.getSubject() instanceof Integer rhs) {
                    if (derived.getRelation().getOperator().apply(value, rhs)) {
                        return Set.of(new Pair<>(derived.getTargetLocal(), derived.getThenAssumption()));
                    }
                }

                return Collections.emptySet();
            });
            var elseValues = AssumptionAnalyzer.extractActualValues(localInfo, assumption -> {
                if (assumption instanceof ConditionalAssumptionByNumberValue<?> derived && derived.getRelation().getRhs() instanceof ConstantRelationObject<?> constantRelationObject && constantRelationObject.getSubject() instanceof Integer rhs) {
                    if (!derived.getRelation().getOperator().apply(value, rhs)) {
                        return Set.of(new Pair<>(derived.getTargetLocal(), derived.getThenAssumption()));
                    }
                }

                return Collections.emptySet();
            });


            var ifEqualSnapshot = current.copy();
            var elseSnapshot = current.copy();

            for (Pair<Local, Assumption> localAssumptionPair : ifValues) {
                var currSnapshot = ifEqualSnapshot.getOrCreateLocalAssumption(localAssumptionPair.getFirst());

                currSnapshot = LinkedAssumptions.and(currSnapshot, localAssumptionPair.getSecond());

                ifEqualSnapshot.putLocalAssumption(localAssumptionPair.getFirst(), currSnapshot);
            }
            for (Pair<Local, Assumption> localAssumptionPair : elseValues) {
                var currSnapshot = elseSnapshot.getOrCreateLocalAssumption(localAssumptionPair.getFirst());

                currSnapshot = LinkedAssumptions.and(currSnapshot, localAssumptionPair.getSecond());

                elseSnapshot.putLocalAssumption(localAssumptionPair.getFirst(), currSnapshot);
            }

            return new Pair<>(ifEqualSnapshot, elseSnapshot);
        } else if (expr instanceof IntegerCompareExpr integerCompareExpr && integerCompareExpr.getOperator() == IntegerCompareExpr.Operator.EQUAL && integerCompareExpr.getLhs() instanceof VarExpr varExprL && integerCompareExpr.getRhs() instanceof VarExpr varExprR) {
            var ifEqualSnapshot = current.copy();
            var elseSnapshot = current.copy();

            var localLHS = varExprL.getLocal();
            var localRHS = varExprR.getLocal();

            {
                var currSnapshotL = ifEqualSnapshot.getOrCreateLocalAssumption(localLHS);
                var currSnapshotR = ifEqualSnapshot.getOrCreateLocalAssumption(localRHS);

                currSnapshotL = LinkedAssumptions.and(
                        currSnapshotL,
                        new NumberRelation<>(new VariableRelationObject<>(varExprR.getLocal(), expr.getRootParent()), IntegerCompareExpr.Operator.EQUAL)
                );
                currSnapshotR = LinkedAssumptions.and(
                        currSnapshotR,
                        new NumberRelation<>(new VariableRelationObject<>(varExprL.getLocal(), expr.getRootParent()), IntegerCompareExpr.Operator.EQUAL)
                );

                ifEqualSnapshot.putLocalAssumption(localLHS, currSnapshotL);
                ifEqualSnapshot.putLocalAssumption(localRHS, currSnapshotR);
            }
            {
                var currSnapshotL = elseSnapshot.getOrCreateLocalAssumption(localLHS);
                var currSnapshotR = elseSnapshot.getOrCreateLocalAssumption(localRHS);

                currSnapshotL = LinkedAssumptions.and(
                        currSnapshotL,
                        new NumberRelation<>(new VariableRelationObject<>(varExprR.getLocal(), expr.getRootParent()), IntegerCompareExpr.Operator.NOT_EQUAL)
                );
                currSnapshotR = LinkedAssumptions.and(
                        currSnapshotR,
                        new NumberRelation<>(new VariableRelationObject<>(varExprL.getLocal(), expr.getRootParent()), IntegerCompareExpr.Operator.NOT_EQUAL)
                );

                elseSnapshot.putLocalAssumption(localLHS, currSnapshotL);
                elseSnapshot.putLocalAssumption(localRHS, currSnapshotR);
            }

            return new Pair<>(ifEqualSnapshot, elseSnapshot);
        }
        /**else if (expr instanceof InstanceOfExpr instanceOfExpr && instanceOfExpr.getInstance() instanceof VarExpr varExpr) {
         var isInstanceSnapshot = current.copy();
         var isNotInstanceSnapshot = current.copy();

         var ifInstance = isInstanceSnapshot.getOrCreateLocalInfo(varExpr.getLocal());
         var ifNotInstance = isNotInstanceSnapshot.getOrCreateLocalInfo(varExpr.getLocal());

         ifInstance = LinkedAssumptions.and(ifInstance, ObjectLocalInfo.create().assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType(instanceOfExpr.getInstanceOfType()))));
         ifNotInstance = LinkedAssumptions.and(ifNotInstance, ObjectLocalInfo.create().assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, true, new ObjectType(instanceOfExpr.getInstanceOfType()))));

         isInstanceSnapshot.putLocalInfo(varExpr.getLocal(), ifInstance);
         isNotInstanceSnapshot.putLocalInfo(varExpr.getLocal(), ifNotInstance);
         }*/

        return new Pair<>(current, current);
    }

    private static boolean mayThrowException(Stmt stmt) {
        for (Expr child : stmt.getChildren()) {
            if (mayThrowException(child)) {
                return true;
            }
        }

        return stmt instanceof MonitorStmt
                || stmt instanceof ArrayStoreStmt // TODO Implement internal bounds check
                || stmt instanceof ThrowStmt;
    }

    private static boolean mayThrowException(Expr expr) {
        if (
                expr instanceof ArrayLoadExpr // TODO Implement internal bounds check
                        || expr instanceof AllocObjectExpr
                        || expr instanceof AllocArrayExpr
                        || expr instanceof CheckCastExpr
                        || expr instanceof InvokeExpr && expr.getMetadata().getProperties().contains(ThrowsProperty.INSTANCE)
        ) {
            return true;
        }

        for (Expr child : expr.getChildren()) {
            if (mayThrowException(child))
                return true;
        }

        return false;
    }

    /**
     * Merges the values in the given stream. The stream may contain null values
     */
    private static Assumption merge(Stream<Assumption> otherInfos) {
        return otherInfos.reduce(null, LocalVariableAnalyzer::mergeNullable);
    }

    /**
     * Merges two local infos which may both be nullable
     */
    private static Assumption mergeNullable(Assumption a, Assumption b) {
        if (a == null)
            return b;
        else if (b == null)
            return a;
        else
            return a.merge(b);
    }

    private static Assumption getObjectAssumptionForReturnType(Type returnType) {
        Assumption objectTypeAssumption = Assumption.NoAssumption.INSTANCE;

        if (returnType.getSort() == Type.ARRAY) {
            objectTypeAssumption = assumeClassIsExactly(new ObjectType(returnType.getInternalName()));
        } else if (returnType.getSort() == Type.OBJECT) {
            objectTypeAssumption = assumeInstanceOf(new ObjectType(returnType.getInternalName()));
        }

        return objectTypeAssumption;
    }

    /**
     * Processes a statement and returns the snapshot that represents the state after the statement
     */
    LocalInfoSnapshot processStatement(Stmt stmt, LocalInfoSnapshot prev) {
        if (mayThrowException(stmt)) {
            prev = prev.copy();

            prev.getCallGraphState().setExceptionState(prev.getCallGraphState().getExceptionState().mayChangeTo(true));
        }

        if (stmt instanceof AbstractCopyStmt) {
            var copied = prev.copy();

            copied.getLocalAssumptions().put(((AbstractCopyStmt) stmt).getVariable().getLocal(), this.processExpression(prev, ((AbstractCopyStmt) stmt).getExpression()));

            return copied;
        } else if (stmt instanceof ThrowStmt) {
            var copied = prev.copy();

            copied.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(true));

            return copied;
        } else if (stmt instanceof ClearExceptionStateStmt) {
            var copied = prev.copy();

            copied.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(false));

            return copied;
        }

        return prev;
    }

    public Assumption processExpression(LocalInfoSnapshot snapshot, Expr expr) {
        var assumptions = processExpression0(snapshot, expr);

        if (this.baseLocalInfoFactory != null) {
            assumptions = this.baseLocalInfoFactory.apply(assumptions, expr);
        }

        return assumptions;
    }

    private Assumption processExpression0(LocalInfoSnapshot snapshot, Expr expr) {
        if (expr instanceof ConstNullExpr) {
            return NullStateAssumption.IS_NULL;
        } else if (expr instanceof AllocObjectExpr) {
            return LinkedAssumptions.and(
                    NullStateAssumption.IS_NON_NULL,
                    assumeClassIsExactly(new ObjectType(((AllocObjectExpr) expr).getAllocatedType()))
            );
        } else if (expr instanceof AllocArrayExpr allocArrayExpr) {
            return LinkedAssumptions.and(
                    NullStateAssumption.IS_NON_NULL,
                    assumeClassIsExactly(new ObjectType("[" + allocArrayExpr.getArrayType()))
            );
        } else if (expr instanceof ConstTypeExpr) {
            return LinkedAssumptions.and(
                    NullStateAssumption.IS_NON_NULL,
                    assumeClassIsExactly(new ObjectType("java/lang/Class"))
            );
        } else if (expr instanceof ConstStringExpr) {
            return LinkedAssumptions.and(
                    NullStateAssumption.IS_NON_NULL,
                    assumeClassIsExactly(new ObjectType("java/lang/String"))
            );
        } else if (expr instanceof ConstIntExpr constIntExpr) {
            return new NumberRelation<>(new ConstantRelationObject<>(constIntExpr.getValue()), IntegerCompareExpr.Operator.EQUAL);
        } else if (expr instanceof ConstLongExpr constLongExpr) {
            return new NumberRelation<>(new ConstantRelationObject<>(constLongExpr.getValue()), IntegerCompareExpr.Operator.EQUAL);
        } else if (expr instanceof ConstBoolExpr constBoolExpr) {
            return new NumberRelation<>(new ConstantRelationObject<>(constBoolExpr.getValue() ? 1 : 0), IntegerCompareExpr.Operator.EQUAL);
        } else if (expr instanceof LoadFieldExpr loadExpr) {
            var fieldDesc = Type.getType(loadExpr.getTarget().getDesc());

            var intrinsicAssumptions = InstrinsicAssumptions.getAssumptionForFieldLoad(this, snapshot, loadExpr);
            var objectTypeAssumption = getObjectAssumptionForReturnType(fieldDesc);

            return LinkedAssumptions.and(intrinsicAssumptions, objectTypeAssumption);
        } else if (expr instanceof ArrayLoadExpr arrayLoadExpr) {
            Expr arr = arrayLoadExpr.getArray();

            if (arr instanceof VarExpr varExpr) {
                var localInfo = snapshot.getOrCreateLocalAssumption(varExpr.getLocal());

                return new ParentArrayAssumption(localInfo);
            }
        } else if (expr instanceof CatchExpr) {
            return NullStateAssumption.IS_NON_NULL;
        } else if (expr instanceof VarExpr varExpr) {
            Assumption linkType;

            if (expr.getType() == ImmType.OBJECT) {
                linkType = new ObjectRelation(new VariableRelationObject<>(varExpr.getLocal(), varExpr.getRootParent()), true);
            } else {
                linkType = new NumberRelation<>(new VariableRelationObject<>(varExpr.getLocal(), varExpr.getRootParent()), IntegerCompareExpr.Operator.EQUAL);
            }

            return LinkedAssumptions.and(snapshot.getLocalAssumption(varExpr.getLocal()), linkType);
        } else if (expr instanceof PhiExpr) {
            return merge(((PhiExpr) expr).getArguments().values().stream().map(x -> this.processExpression(snapshot, x)));
        } else if (expr instanceof ParamExpr paramExpr) {
            var localInfo = this.paramSnapshots[paramExpr.getParamIdx()];

            if (paramExpr.getType() == ImmType.OBJECT) {
                var assumption = localInfo == null ? Assumption.NoAssumption.INSTANCE : this.paramSnapshots[paramExpr.getParamIdx()];

                var isStatic = expr.getBlock().getGraph().getCompilerMethod().isStatic();

                if (!isStatic && paramExpr.getParamIdx() == 0) {
                    assumption = LinkedAssumptions.and(
                            assumption,
                            // The 'this' parameter is never null and must be an instance of the class
                            NullStateAssumption.IS_NON_NULL,
                            assumeInstanceOf(new ObjectType(paramExpr.getBlock().getGraph().getCompilerMethod().getParent().getName()))
                    );
                } else {
                    var argumentTypes = Type.getArgumentTypes(expr.getBlock().getGraph().getCompilerMethod().getNode().desc);

                    var argumentType = argumentTypes[paramExpr.getParamIdx() - (isStatic ? 0 : 1)];

                    var objectAssumptions = assumeInstanceOf(new ObjectType(argumentType.getInternalName()));

                    assumption = LinkedAssumptions.and(assumption, objectAssumptions);
                }

                return assumption;
            }
        } else if (expr instanceof InstanceOfExpr instanceOfExpr && instanceOfExpr.getInstance() instanceof VarExpr varExpr) {
            var ifAssumption = new ObjectTypeAssumption(IS_INSTANCE_OF, false, new ObjectType(instanceOfExpr.getInstanceOfType()));
            var elseAssumption = new ObjectTypeAssumption(IS_INSTANCE_OF, true, new ObjectType(instanceOfExpr.getInstanceOfType()));

            return LinkedAssumptions.and(
                    new ConditionalAssumptionByNumberValue<>(new NumberRelation<>(new ConstantRelationObject<>(0), IntegerCompareExpr.Operator.EQUAL), varExpr.getLocal(), elseAssumption),
                    new ConditionalAssumptionByNumberValue<>(new NumberRelation<>(new ConstantRelationObject<>(0), IntegerCompareExpr.Operator.NOT_EQUAL), varExpr.getLocal(), ifAssumption)
            );
        } else if (expr instanceof CheckCastExpr checkCastExpr && checkCastExpr.getInstance() instanceof VarExpr varExpr) {
            var okAssumption = new ObjectTypeAssumption(IS_INSTANCE_OF, false, new ObjectType(checkCastExpr.getCheckedType()));
            var failAssumption = new ObjectTypeAssumption(IS_INSTANCE_OF, true, new ObjectType(checkCastExpr.getCheckedType()));

            return LinkedAssumptions.and(
                    new ConditionalAssumptionByExceptionState(false, okAssumption),
                    new ConditionalAssumptionByExceptionState(true, failAssumption)
            );
        } else if (expr instanceof InvokeExpr invokeExpr) {
            var intrinsicAssumptions = InstrinsicAssumptions.getAssumptionForInvoke(this, snapshot, invokeExpr);
            var objectTypeAssumption = getObjectAssumptionForReturnType(invokeExpr.getReturnType());

            return LinkedAssumptions.and(intrinsicAssumptions, objectTypeAssumption);
        }

        return Assumption.NoAssumption.INSTANCE;
    }

    public Assumption[] getParameterAssumptionsOfInvoke(LocalInfoSnapshot snapshot, InvokeExpr invokeExpr) {
        var paramExprs = invokeExpr.getChildrenInStackOrder();

        var localInfos = new Assumption[paramExprs.length];

        for (int i = 0; i < paramExprs.length; i++) {
            localInfos[i] = this.processExpression(snapshot, paramExprs[i]);
        }

        return localInfos;
    }

    public void analyze() {
        this.analyze(LocalInfoSnapshot.create(), MAX_BLOCKS);
    }

    public void analyze(LocalInfoSnapshot entrySnapshot, int maxBlocks) {
        if (this.cfg.size() > maxBlocks) {
            fillWithEmptySnapshots();

            return;
        }

        // In the beginning of a method there cannot be a pending exception
        entrySnapshot.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(false));

        var executor = new LocalVariableAnalysisExecutor(this);

        executor.analysisLoop(entrySnapshot);

        this.counter = executor.count;
    }

    private void fillWithEmptySnapshots() {
        var emptySnapshot = LocalInfoSnapshot.create();

        for (BasicBlock vertex : this.cfg.vertices()) {
            this.basicBlockSnapshots.put(vertex, emptySnapshot);

            for (Stmt statement : vertex.getStatements()) {
                this.snapshots.put(statement, emptySnapshot);
            }
        }
    }

//    private void analyzeBasicBlock(HashSet<Integer> finishedBlocks, BasicBlock block, int depth) {
//        this.counter++;
//        this.blockCounts.computeIfAbsent(block.numericId(), key -> new AtomicInteger()).getAndIncrement();
//
//        var currentSnapshot = this.basicBlockSnapshots.get(block);
//
//        if (depth > 200) {
//            // Huh?
//            "".length();
//        }
//
//        // Indicates whether an assumption has changed while processing the block
//        boolean changed = false;
//
//        for (Stmt stmt : block) {
//            currentSnapshot = this.processStatement(stmt, currentSnapshot);
//
//            var lastSnapshot = this.snapshots.put(stmt, currentSnapshot);
//
//            if (lastSnapshot == null || !lastSnapshot.isEquivalent(currentSnapshot)) {
//                changed = true;
//            }
//        }
//
//        // If any assumption has changed, other subsequent assumptions might have to change as well
//        // so every following block has to be processed again.
//        if (changed) {
//            finishedBlocks.clear();
//        }
//
//        var isNew = finishedBlocks.add(block.numericId());
//
//        // Process the subsequent if the block wasn't processed before
//        if (isNew) {
//            var terminatorStmt = block.getTerminator();
//
//            if (terminatorStmt instanceof BranchStmt) {
//                Pair<BasicBlock, LocalInfoSnapshot>[] successiveBlockSnapshots = getSuccessiveBlockSnapshots(currentSnapshot, (BranchStmt) terminatorStmt);
//
//                for (Pair<BasicBlock, LocalInfoSnapshot> successiveBlockSnapshot : successiveBlockSnapshots) {
//                    var outSnapshot = this.basicBlockSnapshots.get(successiveBlockSnapshot.getFirst());
//
//                    // If the block has been traversed in the past, the resulting snapshot may not be overwritten
//                    if (outSnapshot != null) {
//                        var oldSnapshot = outSnapshot;
//
//                        outSnapshot = outSnapshot.merge(successiveBlockSnapshot.getSecond());
//                    } else {
//                        outSnapshot = successiveBlockSnapshot.getSecond();
//                    }
//
//                    var next = outSnapshot;
//                    var prev = this.basicBlockSnapshots.put(successiveBlockSnapshot.getFirst(), outSnapshot);
//
//                    if (prev == null || next == null || !prev.isEquivalent(next)) {
//                        finishedBlocks.clear();
//                    }
//                }
//
//                if (this.sccIndex != null) {
//                    var currIdx = this.sccIndex.get(block);
//
//                    Arrays.sort(successiveBlockSnapshots, Comparator.comparingInt(x -> Math.abs(this.sccIndex.get(x.getFirst()) - currIdx)));
//                }
//
//                // Analyze the following basic blocks
//                for (Pair<BasicBlock, LocalInfoSnapshot> successiveBlockSnapshot : successiveBlockSnapshots) {
//                    analyzeBasicBlock(finishedBlocks, successiveBlockSnapshot.getFirst(), depth + 1);
//                }
//            }
//        }
//    }

    public LocalInfoSnapshot getStatementSnapshot(Stmt stmt) {
        return this.snapshots.get(stmt);
    }

    public LocalInfoSnapshot getBlockSnapshot(BasicBlock block) {
        return this.basicBlockSnapshots.get(block);
    }
}
