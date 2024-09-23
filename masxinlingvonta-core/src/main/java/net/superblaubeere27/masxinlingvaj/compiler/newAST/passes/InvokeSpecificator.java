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
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldName;

import java.util.ArrayList;
import java.util.Collections;

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

                    var impls = new ArrayList<>(ClassHierarchyBuilder.getPossibleImplementationsCached(this.compilerIndex, clazz, new MethodOrFieldName(call.getTarget())));

                    if (call.getInstanceExpr() instanceof VarExpr varExpr) {
                        var assumption = analyzer.getStatementSnapshot(stmt).getOrCreateLocalAssumption(varExpr.getLocal());

                        // Find out discrete values for object type
                        var typeAssumptions = AssumptionAnalyzer.extractActualValues(assumption, AssumptionPredicates.GET_TYPE_ASSUMPTION_PREDICATE_SET);

                        // If discrete values where found, filter by them
                        for (ObjectTypeAssumption typeAssumption : typeAssumptions) {
                            impls = filterByAssumptionState(typeAssumption, new MethodOrFieldName(call.getTarget()), impls);
                        }
                    }

                    // Check if the call can be turned into a more specific call
                    if (impls.size() != 1)
                        continue;

                    var actualTarget = impls.get(0);

                    call.setType(InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL);
                    call.setTarget(actualTarget.getIdentifier());
                }
            }
        }
    }

    /**
     * Sorts out all method implementations that are no candidates due to the assumption assumption {@code assumption}
     */
    private ArrayList<CompilerMethod> filterByAssumptionState(ObjectTypeAssumption assumption, MethodOrFieldName methodName, ArrayList<CompilerMethod> candidates) {
        // Arrays have weird ass logic which we don't want to handle yet.
        if (!assumption.getType().isObject())
            return candidates;

        var typeOfObject = assumption.getType().getTypeOfObject();
        var searchedClass = this.compilerIndex.getClass(typeOfObject);

        if (searchedClass == null)
            throw new IllegalStateException("Unable to find '" + typeOfObject + "'");

        switch (assumption.getRelation()) {
            case IS_EXACTLY -> {
                // The actual implementation of the method
                var virtualImplementation = ClassHierarchyBuilder.getVirtualImplementation(this.compilerIndex, searchedClass, methodName);

                if (virtualImplementation == null) {
                    return candidates;
                }

                if (assumption.isInverted()) {
                    candidates.remove(virtualImplementation);
                } else {
                    return new ArrayList<>(Collections.singletonList(virtualImplementation));
                }
            }
            case IS_INSTANCE_OF -> {
                var clazz = this.compilerIndex.getClass(typeOfObject);

                if (!assumption.isInverted()) {
                    var implementations = ClassHierarchyBuilder.getPossibleImplementations(this.compilerIndex, clazz, methodName);

                    candidates.removeIf(candidate -> !implementations.contains(candidate));

                } else {
                    candidates.removeIf(candidate -> ClassHierarchyBuilder.isInstanceOf(candidate.getParent(), clazz));
                }
            }
        }

        return candidates;
    }

}
