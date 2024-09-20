package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionPredicates;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldName;

import java.util.*;

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
                        var assumption = analyzer.getStatementSnapshot(stmt).getOrCreateLocalInfo(varExpr.getLocal());

                        // Find out discrete values for object type
                        var typeAssumptions = AssumptionAnalyzer.extractActualValues(assumption, AssumptionPredicates.GET_TYPE_ASSUMPTION_PREDICATE_SET);

                        // If discrete values where found, filter by them
                        for (ObjectTypeAssumptionState typeAssumption : typeAssumptions) {
                            impls = filterByAssumptionState(typeAssumption, new MethodOrFieldName(call.getTarget()), impls);
                        }
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

    private List<CompilerMethod> filterByAssumptionStates(HashSet<ObjectTypeAssumptionState> objectTypeAssumptionStates, MethodOrFieldName methodOrFieldName, List<CompilerMethod> impls) {
        HashSet<CompilerMethod> possibleImplementations = new HashSet<>();

        for (ObjectTypeAssumptionState objectTypeAssumptionState : objectTypeAssumptionStates) {
            possibleImplementations.addAll(filterByAssumptionState(objectTypeAssumptionState, methodOrFieldName, impls));
        }

        return new ArrayList<>(possibleImplementations);
    }

    /**
     * Sorts out all method implementations that are no candidates due to the assumption state {@code state}
     */
    private List<CompilerMethod> filterByAssumptionState(ObjectTypeAssumptionState state, MethodOrFieldName methodName, List<CompilerMethod> currentCandidates) {
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

        for (ObjectTypeAssumptionState.ObjectTypeInfo knownInfo : state.getKnownInfos()) {
            // Arrays have weird ass logic
            if (!knownInfo.type().isObject())
                continue;

            var typeName = knownInfo.type().getTypeOfObject();
            var clazz = this.compilerIndex.getClass(typeName);


            if (knownInfo.relation() != ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF) {
                continue;
            }

            if (!knownInfo.inverted()) {
                var virtualImplementation = ClassHierarchyBuilder.getVirtualImplementation(this.compilerIndex, clazz, methodName);
                var implementations = ClassHierarchyBuilder.getPossibleImplementations(this.compilerIndex, clazz, methodName);

                if (implementations.isEmpty() || virtualImplementation == null) {
                    continue;
                }

                remainingCandidates.removeIf(candidate -> !implementations.contains(candidate));
            } else {
                remainingCandidates.removeIf(candidate -> ClassHierarchyBuilder.isInstanceOf(candidate.getParent(), clazz));
            }
        }

        return remainingCandidates;
    }

}
