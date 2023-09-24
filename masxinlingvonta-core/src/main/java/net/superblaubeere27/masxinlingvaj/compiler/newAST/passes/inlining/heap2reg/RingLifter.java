package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heap2reg;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.LocalRingAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.PutFieldStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerField;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldName;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Does the actual heavy lifting (xD)
 */
class RingLifter {
    private final ControlFlowGraph cfg;
    private final LocalRingAnalyzer.LocalVariableRing ring;
    private final HashMap<MethodOrFieldName, Local> fieldMap;

    public RingLifter(ControlFlowGraph cfg, LocalRingAnalyzer.LocalVariableRing ring, CompilerClass targetClass) {
        this.cfg = cfg;
        this.ring = ring;
        this.fieldMap = recursivelyAllocateFields(targetClass, cfg);
    }

    /**
     * Remaps all references to a lifted ring so field accesses now correspond to the newly allocated registers
     */
    void lift(StatementTransaction stmtTransaction) {
        for (Local variable : ring.getVariables()) {
            remapObjectAllocations(stmtTransaction, variable);

            remapFieldAccesses(stmtTransaction, variable);
        }
    }

    private void remapFieldAccesses(StatementTransaction stmtTransaction, Local variable) {
        for (VarExpr varExpr : cfg.getLocals().uses.get(variable)) {
            var parent = varExpr.getParent();

            if (parent instanceof PutFieldStmt) {
                var local = fieldMap.get(new MethodOrFieldName(((PutFieldStmt) parent).getTarget()));

                if (local == null)
                    "".length();

                stmtTransaction.replaceStatement((Stmt) parent, new CopyVarStmt(new VarExpr(local), ((PutFieldStmt) parent).getValue().copy()));
            } else if (parent instanceof GetFieldExpr) {
                stmtTransaction.replaceExpr((Expr) parent, new VarExpr(fieldMap.get(new MethodOrFieldName(((GetFieldExpr) parent).getTarget()))));
            } else if (parent instanceof PhiExpr) {

            } else {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Remaps object allocations.
     * <p>
     * Object allocations are replaced by inserting null values into the registers corresponding to the fields
     */
    private void remapObjectAllocations(StatementTransaction stmtTransaction, Local variable) {
        AbstractCopyStmt def = cfg.getLocals().defs.get(variable);

        if (!(def.getExpression() instanceof AllocObjectExpr)) {
            // Why is it bad if we hit this exception? Instances can only be provided to the ring via
            // alloc object expr, we have already checked for that
            throw new IllegalStateException();
        }

        var replacement = fieldMap
                .values()
                .stream()
                .map(local -> (Stmt) new CopyVarStmt(new VarExpr(local), local.getType().createConstNull()))
                .collect(Collectors.toList());

        stmtTransaction.replaceStatement(def, replacement);
    }

    /**
     * Allocates a synthetic local for every field a class effectively has (= with all superclasses)
     * and returns then in a map
     */
    private HashMap<MethodOrFieldName, Local> recursivelyAllocateFields(CompilerClass targetClass, ControlFlowGraph cfg) {
        HashMap<MethodOrFieldName, Local> fieldMap = new HashMap<>();

        recursivelyAllocateFields0(targetClass, fieldMap, cfg);

        return fieldMap;
    }

    private void recursivelyAllocateFields0(CompilerClass targetClass, HashMap<MethodOrFieldName, Local> fieldMap, ControlFlowGraph cfg) {
        var superClass = targetClass.getRelations().getSuperClass();

        if (superClass != null)
            recursivelyAllocateFields0(superClass, fieldMap, cfg);

        for (CompilerField field : targetClass.getFields()) {
            fieldMap.put(new MethodOrFieldName(field.getIdentifier()), cfg.getLocals().allocSynthetic(ImmType.fromJVMType(Type.getType(field.getNode().desc))));
        }
    }

}
