package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public class ExpressionStmt extends Stmt {
    private Expr expression;

    public ExpressionStmt(Expr expression) {
        super(EXPR);

        writeAt(expression, 0);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.expression = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getExpression() {
        return expression;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        this.expression.toString(printer);

        printer.print(';');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ExpressionStmt && ((ExpressionStmt) s).expression.equivalent(this.expression);
    }

    @Override
    public Stmt copy() {
        return new ExpressionStmt(this.expression.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileExpressionStmt(this);
    }
}
