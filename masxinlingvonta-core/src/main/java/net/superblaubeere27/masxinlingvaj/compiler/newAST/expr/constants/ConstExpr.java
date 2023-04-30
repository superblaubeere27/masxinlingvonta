package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;

public abstract class ConstExpr extends Expr {
    public ConstExpr(int opcode) {
        super(opcode);
    }
}
