package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public abstract class AbstractCopyStmt extends Stmt {
    /**
     * The RHS expression (source).
     */
    private Expr expression;
    /**
     * The LHS variable (destination).
     */
    private VarExpr variable;

    public AbstractCopyStmt(int opcode, VarExpr variable, Expr expression) {
        super(opcode);

        if (variable == null || expression == null)
            throw new IllegalArgumentException("Neither variable nor statement can be null!");

        this.expression = expression;
        this.variable = variable;

        writeAt(expression, 0);
    }

    public VarExpr getVariable() {
        return variable;
    }

    public void setVariable(VarExpr var) {
        this.onRemoval(this.getBlock());

        this.variable = var;

        this.onAddition(this.getBlock());
    }

    public Expr getExpression() {
        return expression;
    }

    public void setExpression(Expr expression) {
        writeAt(expression, 0);
    }

    public ImmType getType() {
        return variable.getType();
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            expression = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }

    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(toString());
    }

    @Override
    public String toString() {
        return variable + " = " + expression + ";";
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    public boolean isRedundant() {
        return expression instanceof VarExpr && ((VarExpr) expression).getLocal() == variable.getLocal();
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileCopyStmt(this);
    }

    @Override
    public abstract AbstractCopyStmt copy();

    @Override
    public abstract boolean equivalent(CodeUnit s);

    @Override
    public final void onAddition(BasicBlock basicBlock) {
        super.onAddition(basicBlock);

        var previous = basicBlock.cfg.getLocals().defs.put(this.variable.getLocal(), this);

//        assert !this.getVariable().getLocal().isSSA() || previous == null;
    }

    @Override
    public final void onRemoval(BasicBlock basicBlock) {
        super.onRemoval(basicBlock);

        basicBlock.cfg.getLocals().defs.remove(this.variable.getLocal(), this);
    }
}