package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

public abstract class LoadFieldExpr extends Expr {
    protected final MethodOrFieldIdentifier target;

    public LoadFieldExpr(int opcode, MethodOrFieldIdentifier target) {
        super(opcode);
        this.target = target;
    }

    public MethodOrFieldIdentifier getTarget() {
        return target;
    }
}
