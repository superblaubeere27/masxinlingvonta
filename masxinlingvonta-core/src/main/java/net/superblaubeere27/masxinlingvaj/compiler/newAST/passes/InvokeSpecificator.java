package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.*;

import java.util.*;
import java.util.stream.Stream;

public class InvokeSpecificator extends Pass {
    private final CompilerIndex compilerIndex;

    public InvokeSpecificator(CompilerIndex compilerIndex) {
        this.compilerIndex = compilerIndex;
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        LocalVariableAnalyzer analyzer = new LocalVariableAnalyzer(cfg);

        analyzer.analyze();

        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : vertex) {
                for (Expr expr : stmt.enumerateOnlyChildren()) {
                    if (!(expr instanceof InvokeInstanceExpr call))
                        continue;

                    if (call.getInvokeType() == InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL)
                        continue;

                    var clazz = this.compilerIndex.getClass(call.getTarget().getOwner());

                    if (clazz == null)
                        continue;

                    var impls = ClassHierarchyBuilder.getPossibleImplementationsCached(this.compilerIndex, clazz, new MethodOrFieldName(call.getTarget()));

                    if (call.getInstanceExpr() instanceof VarExpr varExpr) {
                        var assumption = analyzer.getStatementSnapshot(stmt).getOrCreateObjectLocalInfo(varExpr.getLocal()).getObjectTypeAssumption();

                        impls = filterByAssumptions(assumption, new MethodOrFieldName(call.getTarget()), impls);
                    }

                    // Check if the call can be turned into a more specific call
                    if (impls == null || impls.size() != 1)
                        continue;

                    var actualTarget = impls.get(0);

                    call.setType(InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL);
                    call.setTarget(actualTarget.getIdentifier());
                }
            }
        }
    }

    /**
     * Sorts out all method implementations that are no candidates due to the assumption state {@code state}
     */
    private List<CompilerMethod> filterByAssumptions(ObjectTypeAssumptionState state, MethodOrFieldName methodName, List<CompilerMethod> currentCandidates) {
        // Find out if we know the type
        Optional<ObjectType> exactTypeIfKnown = state.getExactTypeIfKnown();

        // If we know exactly which type it is, just do a vtable-lookup
        if (exactTypeIfKnown.isPresent() && !exactTypeIfKnown.get().isArray()) {
            var searchedClass = this.compilerIndex.getClass(exactTypeIfKnown.get().getTypeOfObject());

            if (searchedClass == null)
                throw new IllegalStateException("Unable to find '" + exactTypeIfKnown.get().getTypeOfObject() + "'");

            return Collections.singletonList(ClassHierarchyBuilder.getVirtualImplementation(this.compilerIndex, searchedClass, methodName));
        }

        var remainingCandidates = new ArrayList<>(currentCandidates);

        remainingCandidates.removeIf(candidate -> {
            for (ObjectTypeAssumptionState.ObjectTypeInfo knownInfo : state.getKnownInfos()) {
                if (!knownInfo.type().isObject())
                    continue;

                var typeName = knownInfo.type().getTypeOfObject();

                var virtualImplementation = ClassHierarchyBuilder.getVirtualImplementation(this.compilerIndex, this.compilerIndex.getClass(typeName), methodName);

                if (knownInfo.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF) {
                    var isInstanceOf = ClassHierarchyBuilder.isInstanceOf(candidate.getParent(), compilerIndex.getClass(typeName));

                    if (!isInstanceOf && !knownInfo.inverted() && !virtualImplementation.getIdentifier().equals(candidate.getIdentifier()))
                        return true;

                    if (isInstanceOf && knownInfo.inverted()) {
                        return true;
                    }
                }
            }

            return false;
        });

        return remainingCandidates;
    }

}
