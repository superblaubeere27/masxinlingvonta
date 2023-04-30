package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ParamExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstNullExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstStringExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstTypeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.LoadFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.AllocArrayExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLoadExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectLocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ArrayStoreStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ClearExceptionStateStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.MonitorStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ThrowStmt;
import net.superblaubeere27.masxinlingvaj.utils.Pair;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Stream;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectTypeAssumptionState.ObjectTypeInfo.isExactly;
import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectTypeAssumptionState.ObjectTypeInfo.isInstanceOf;

public class LocalVariableAnalyzer {
    private final ControlFlowGraph cfg;
    private final HashMap<Stmt, LocalInfoSnapshot> snapshots = new HashMap<>();
    /**
     * Snapshots that represents the state in the beginning of the block
     */
    private final HashMap<BasicBlock, LocalInfoSnapshot> basicBlockSnapshots = new HashMap<>();


    public LocalVariableAnalyzer(ControlFlowGraph cfg) {
        this.cfg = cfg;
    }

    /**
     * Returns the blocks that a branch can jump to, combined with the assumptions that can be drawn from the specific jump
     */
    private static Pair<BasicBlock, LocalInfoSnapshot>[] getSuccessiveBlockSnapshots(LocalInfoSnapshot currentSnapshot, BranchStmt branchStmt) {
        if (branchStmt instanceof ConditionalBranch) {
            var condBranch = ((ConditionalBranch) branchStmt);
            var assumptionsOfTrueAndFalseValue = getAssumptionsOfTrueAndFalseValue(currentSnapshot, condBranch.getCond());

            return new Pair[]{new Pair<>(condBranch.getIfTarget(), assumptionsOfTrueAndFalseValue.getFirst()), new Pair<>(condBranch.getElseTarget(), assumptionsOfTrueAndFalseValue.getSecond())};
        }

        return Arrays.stream(branchStmt.getNextBasicBlocks()).map(x -> new Pair<>(x, currentSnapshot)).toArray(Pair[]::new);
    }

    /**
     * Basically tells what can be assumed if bool expression results in a true or false value
     */
    private static Pair<LocalInfoSnapshot, LocalInfoSnapshot> getAssumptionsOfTrueAndFalseValue(LocalInfoSnapshot current, Expr expr) {
        if (expr instanceof ObjectCompareExpr) {
            ObjectCompareExpr compareExpr = (ObjectCompareExpr) expr;

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

            var ifEquals = ifEqualSnapshot.getOrCreateObjectLocalInfo(comparedVar.getLocal());
            var ifNotEquals = ifNotEqualSnapshot.getOrCreateObjectLocalInfo(comparedVar.getLocal());

            if (otherExpr instanceof ConstNullExpr) {
                ifEquals = ifEquals.assumeIsNull(true);
                ifNotEquals = ifEquals.assumeIsNull(false);
            }

            ifEqualSnapshot.putLocalInfo(comparedVar.getLocal(), ifEquals);
            ifNotEqualSnapshot.putLocalInfo(comparedVar.getLocal(), ifNotEquals);

            return new Pair<>(ifEqualSnapshot, ifNotEqualSnapshot);
        } else if (expr instanceof InstanceOfExpr instanceOfExpr && instanceOfExpr.getInstance() instanceof VarExpr varExpr) {
            var isInstanceSnapshot = current.copy();
            var isNotInstanceSnapshot = current.copy();

            var ifInstance = isInstanceSnapshot.getOrCreateObjectLocalInfo(varExpr.getLocal());
            var ifNotInstance = isNotInstanceSnapshot.getOrCreateObjectLocalInfo(varExpr.getLocal());

            ifInstance = ifInstance.assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType(instanceOfExpr.getInstanceOfType())));
            ifNotInstance = ifInstance.assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, true, new ObjectType(instanceOfExpr.getInstanceOfType())));

            isInstanceSnapshot.putLocalInfo(varExpr.getLocal(), ifInstance);
            isNotInstanceSnapshot.putLocalInfo(varExpr.getLocal(), ifNotInstance);
        }

        return new Pair<>(current, current);
    }

    /**
     * Processes a statement and returns the snapshot that represents the state after the statement
     */
    private static LocalInfoSnapshot processStatement(Stmt stmt, LocalInfoSnapshot prev) {
        if (mayThrowException(stmt)) {
            prev = prev.copy();

            prev.getCallGraphState().setExceptionState(prev.getCallGraphState().getExceptionState().mayChangeTo(true));
        }

        if (stmt instanceof AbstractCopyStmt) {
            var copied = prev.copy();

            copied.getLocalInfos().put(((AbstractCopyStmt) stmt).getVariable().getLocal(), processExpression(prev, ((AbstractCopyStmt) stmt).getExpression()));

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
                        || expr instanceof InvokeExpr
        ) {
            return true;
        }

        for (Expr child : expr.getChildren()) {
            if (mayThrowException(child))
                return true;
        }

        return false;
    }

    private static LocalInfo processExpression(LocalInfoSnapshot snapshot, Expr expr) {
        if (expr instanceof ConstNullExpr) {
            return ObjectLocalInfo.create().assumeIsNull(true);
        } else if (expr instanceof AllocObjectExpr) {
            return ObjectLocalInfo.create().assumeIsNull(false).assumeObjectType(isExactly(new ObjectType(((AllocObjectExpr) expr).getAllocatedType())));
        } else if (expr instanceof AllocArrayExpr allocArrayExpr) {
            return ObjectLocalInfo.create().assumeIsNull(false).assumeObjectType(isExactly(new ObjectType("[" + allocArrayExpr.getArrayType())));
        } else if (expr instanceof ConstTypeExpr) {
            return ObjectLocalInfo.create().assumeIsNull(false).assumeObjectType(isExactly(new ObjectType("java/lang/Class")));
        } else if (expr instanceof ConstStringExpr) {
            return ObjectLocalInfo.create().assumeIsNull(false).assumeObjectType(isExactly(new ObjectType("java/lang/String")));
        } else if (expr instanceof LoadFieldExpr loadExpr) {
            var fieldDesc = Type.getType(loadExpr.getTarget().getDesc());

            if (fieldDesc.getSort() == Type.ARRAY)
                return ObjectLocalInfo.create().assumeObjectType(isExactly(new ObjectType(fieldDesc.getInternalName())));

            if (fieldDesc.getSort() == Type.OBJECT)
                return ObjectLocalInfo.create().assumeObjectType(isInstanceOf(new ObjectType(fieldDesc.getInternalName())));
        } else if (expr instanceof ArrayLoadExpr arrayLoadExpr) {
            Expr arr = arrayLoadExpr.getArray();

            if (arr instanceof VarExpr varExpr) {
                ObjectLocalInfo localInfo = snapshot.getOrCreateObjectLocalInfo(varExpr.getLocal());

                var newLocalInfo = ObjectLocalInfo.create();

                for (ObjectTypeAssumptionState.ObjectTypeInfo knownInfo : localInfo.getObjectTypeAssumption().getKnownInfos()) {
                    var innerType = knownInfo.type().getArrayElementType();

                    if (innerType.getSort() != Type.OBJECT && innerType.getSort() != Type.ARRAY)
                        continue;

                    newLocalInfo = newLocalInfo.assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(knownInfo.relation(), false, new ObjectType(innerType.getInternalName())));
                }

                return newLocalInfo;
            }
        } else if (expr instanceof CatchExpr) {
            return ObjectLocalInfo.create().assumeIsNull(false);
        } else if (expr instanceof VarExpr) {
            return snapshot.getLocalInfos().get(((VarExpr) expr).getLocal());
        } else if (expr instanceof PhiExpr) {
            return merge(((PhiExpr) expr).getArguments().values().stream().map(x -> processExpression(snapshot, x)));
        } else if (expr instanceof ParamExpr paramExpr) {
            if (paramExpr.getType() == ImmType.OBJECT) {
                var assumption = ObjectLocalInfo.create();

                var isStatic = expr.getBlock().getGraph().getCompilerMethod().isStatic();

                if (!isStatic && paramExpr.getParamIdx() == 0) {
                    // The 'this' parameter is never null and must be an instance of the class
                    assumption = assumption
                            .assumeIsNull(false)
                            .assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType(paramExpr.getBlock().getGraph().getCompilerMethod().getParent().getName())));
                } else {
                    var argumentTypes = Type.getArgumentTypes(expr.getBlock().getGraph().getCompilerMethod().getNode().desc);

                    var argumentType = argumentTypes[paramExpr.getParamIdx() - (isStatic ? 0 : 1)];

                    assumption = assumption.assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType(argumentType.getInternalName())));
                }

                return assumption;
            }
        } else if (expr.getType() == ImmType.OBJECT) {
            return ObjectLocalInfo.create();
        }

        if (expr.getType() == ImmType.OBJECT)
            throw new IllegalArgumentException();

        return null;
    }

    /**
     * Merges the values in the given stream. The stream may contain null values
     */
    private static LocalInfo merge(Stream<LocalInfo> otherInfos) {
        return otherInfos.reduce(null, LocalVariableAnalyzer::mergeNullable);
    }

    /**
     * Merges two local infos which may both be nullable
     */
    private static LocalInfo mergeNullable(LocalInfo a, LocalInfo b) {
        if (a == null)
            return b;
        else if (b == null)
            return a;
        else
            return a.merge(b);
    }

    /**
     * Merges two local infos which may both be nullable
     */
    private static LocalInfoSnapshot mergeNullable(LocalInfoSnapshot a, LocalInfoSnapshot b) {
        if (a == null)
            return b;
        else if (b == null)
            return a;
        else
            return a.merge(b);
    }

    public void analyze() {
        var entrySnapshot = LocalInfoSnapshot.create();

        // In the beginning of a method there cannot be a pending exception
        entrySnapshot.getCallGraphState().setExceptionState(PrimitiveAssumptionState.assume(false));

        this.basicBlockSnapshots.put(this.cfg.getEntry(), entrySnapshot);

        analyzeBasicBlock(new HashSet<>(), this.cfg.getEntry());
    }

    private void analyzeBasicBlock(HashSet<Integer> finishedBlocks, BasicBlock block) {
        var currentSnapshot = this.basicBlockSnapshots.get(block);

        // Indicates whether an assumption has changed while processing the block
        boolean changed = false;

        for (Stmt stmt : block) {
            currentSnapshot = processStatement(stmt, currentSnapshot);

            var lastSnapshot = this.snapshots.put(stmt, currentSnapshot);

            if (lastSnapshot == null || !lastSnapshot.isEquivalent(currentSnapshot)) {
                changed = true;
            }
        }

        // If any assumption has changed, other subsequent assumptions might have to change as well
        // so every following block has to be processed again.
        if (changed) {
            finishedBlocks.clear();
        }

        var isNew = finishedBlocks.add(block.getNumericId());

        // Process the subsequent if the block wasn't processed before
        if (isNew) {
            var terminatorStmt = block.getTerminator();

            if (terminatorStmt instanceof BranchStmt) {
                Pair<BasicBlock, LocalInfoSnapshot>[] successiveBlockSnapshots = getSuccessiveBlockSnapshots(currentSnapshot, (BranchStmt) terminatorStmt);

                for (Pair<BasicBlock, LocalInfoSnapshot> successiveBlockSnapshot : successiveBlockSnapshots) {
                    var outSnapshot = this.basicBlockSnapshots.get(successiveBlockSnapshot.getFirst());

                    var crapshot = outSnapshot;

                    // If the block has been traversed in the past, the resulting snapshot may not be overwritten
                    if (outSnapshot != null) {
                        outSnapshot = outSnapshot.merge(successiveBlockSnapshot.getSecond());
                    } else {
                        outSnapshot = successiveBlockSnapshot.getSecond();
                    }

                    var prev = outSnapshot;
                    var next = this.basicBlockSnapshots.put(successiveBlockSnapshot.getFirst(), outSnapshot);

                    if (prev == null || next == null || !prev.isEquivalent(next)) {
                        prev.equals(next);
                    }
                }

                // Analyze the following basic blocks
                for (Pair<BasicBlock, LocalInfoSnapshot> successiveBlockSnapshot : successiveBlockSnapshots) {
                    analyzeBasicBlock(finishedBlocks, successiveBlockSnapshot.getFirst());
                }
            }
        }
    }

    public LocalInfoSnapshot getStatementSnapshot(Stmt stmt) {
        return this.snapshots.get(stmt);
    }

}
