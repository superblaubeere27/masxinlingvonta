package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;

public class CopyVarStmt extends AbstractCopyStmt {

    public CopyVarStmt(VarExpr variable, Expr expression) {
        super(LOCAL_STORE, variable, expression);

        assert (!(expression instanceof PhiExpr));
    }

    @Override
    public CopyVarStmt copy() {
        return new CopyVarStmt(getVariable().copy(), getExpression().copy());
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (s instanceof CopyVarStmt) {
            CopyVarStmt copy = (CopyVarStmt) s;
            return getExpression().equivalent(copy.getExpression()) && getVariable().equivalent(copy.getVariable());
        }
        return false;
    }
}
