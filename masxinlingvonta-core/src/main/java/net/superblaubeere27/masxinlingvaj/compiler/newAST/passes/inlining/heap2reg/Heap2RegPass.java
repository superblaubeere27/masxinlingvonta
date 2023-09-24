package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heap2reg;

import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.RegisterToSSA;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.Pass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalRingAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutFieldStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

public class Heap2RegPass extends Pass {
    private final CompilerIndex index;

    public Heap2RegPass(CompilerIndex index) {
        this.index = index;
    }

    /**
     * This function fulfills two purposes:
     * <ol>
     *     <li>Checks if this local variable ring is viable for lifting</li>
     *     <li>Returns the type of the class that this ring has</li>
     * </ol>
     * <p>
     * A ring can be lifted if:
     * <ol>
     *     <li>All instructions acting on it are viable (see {@link Heap2RegPass#isViableUsage(CodeUnit)})</li>
     *     <li>All instructions providing an instance to the ring allocate the object</li>
     *     <li>There is a definite class type (i.e. if a ring can contain <code>class A extends C</code> or
     *     <code>class B extends C</code> then it is not possible to lift it (yet...))</li>
     * </ol>
     *
     * @param cfg
     * @param ring
     * @return
     */
    private static String checkAndGetAllocationType(ControlFlowGraph cfg, LocalRingAnalyzer.LocalVariableRing ring) {
        LocalsPool locals = cfg.getLocals();

        String allocationType = null;

        for (Local variable : ring.getVariables()) {
            // Check if there are any instructions that reference the object that would disqualify it from being lifted
            // into registers
            for (VarExpr varExpr : locals.uses.getNonNull(variable)) {
                if (!isViableUsage(varExpr.getParent(), ring))
                    return null;
            }

            Expr declaringExpr = locals.defs.get(variable).getExpression();

            // An object can only be lifted if it is allocated on the same function
            if (!(declaringExpr instanceof AllocObjectExpr))
                return null;

            String currentType = ((AllocObjectExpr) declaringExpr).getAllocatedType();

            // Does the type of class this expression allocates differ from the other expressions that were found
            if (allocationType != null && !currentType.equals(allocationType))
                return null;

            allocationType = currentType;
        }

        return allocationType;
    }

    /**
     * Does calling this expression on an object instance disqualify the object from being pulled into registers?
     */
    private static boolean isViableUsage(CodeUnit parent, LocalRingAnalyzer.LocalVariableRing ring) {
        var ringVars = ring.getVariables();

        if (parent instanceof GetFieldExpr getField && getField.getInstance() instanceof VarExpr varExpr && ringVars.contains(varExpr.getLocal()))
            return true;
        if (parent instanceof PutFieldStmt getField && getField.getInstance() instanceof VarExpr varExpr && ringVars.contains(varExpr.getLocal()))
            return true;

        return parent instanceof PhiExpr;
    }

    @Override
    public void apply(ControlFlowGraph cfg) {
        LocalRingAnalyzer analyzer = LocalRingAnalyzer.buildLocalRing(cfg);

        StatementTransaction stmtTransaction = new StatementTransaction();

        for (LocalRingAnalyzer.LocalVariableRing ring : analyzer.getRings()) {
            String allocationType = checkAndGetAllocationType(cfg, ring);

            // Did the check fail?
            if (allocationType == null)
                continue;

            // Find the allocated class type
            var targetClass = this.index.getClass(allocationType);

            if (targetClass == null)
                continue;

            System.out.println("Lifting " + targetClass.getName() + " in " + cfg.getCompilerMethod().getIdentifier());

            // Lift
            RingLifter lifter = new RingLifter(cfg, ring, targetClass);

            lifter.lift(stmtTransaction);

            LocalRingAnalyzer.buildLocalRing(cfg);
        }

        if (stmtTransaction.apply()) {
            // We only converted the class accesses into register form, but we will need it in SSA form to continue
            RegisterToSSA registerToSSA = new RegisterToSSA(cfg);

            registerToSSA.process();
        }
    }

}
