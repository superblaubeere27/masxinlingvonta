package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public class RetStmt extends Stmt {
    private Expr value;

    public RetStmt(Expr value) {
        super(RET);

        // Yes, this is redundant
        this.value = value;

        writeAt(value, 0);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.value = read(ptr);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getValue() {
        return value;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("ret " + this.value);
    }

    @Override
    public boolean isTerminating() {
        return true;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof RetStmt && ((RetStmt) s).value.equivalent(this.value);
    }

    @Override
    public Stmt copy() {
        return new RetStmt(this.value.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileRet(this);
    }

}
