package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;

public class CopyPhiStmt extends AbstractCopyStmt {

    public CopyPhiStmt(VarExpr variable, PhiExpr expression) {
        super(PHI_STORE, variable, expression);
    }

    @Override
    public PhiExpr getExpression() {
        return (PhiExpr) super.getExpression();
    }

    @Override
    public void setExpression(Expr expression) {
        if (expression != null && !(expression instanceof PhiExpr)) {
            throw new UnsupportedOperationException(expression.toString());
        }

        super.setExpression(expression);
    }

    @Override
    public CopyPhiStmt copy() {
        return new CopyPhiStmt(getVariable().copy(), getExpression().copy());
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (s instanceof CopyPhiStmt) {
            CopyPhiStmt copy = (CopyPhiStmt) s;
            return getExpression().equivalent(copy.getExpression()) && getVariable().equivalent(copy.getVariable());
        }
        return false;
    }
}