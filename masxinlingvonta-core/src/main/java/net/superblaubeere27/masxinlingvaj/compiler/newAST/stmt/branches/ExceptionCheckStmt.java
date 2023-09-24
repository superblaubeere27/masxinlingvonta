package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public class ExceptionCheckStmt extends BranchStmt {
    public static final int ON_OK_IDX = 0;
    public static final int ON_EXCEPTION_IDX = 1;

    public ExceptionCheckStmt(BasicBlock okTarget, BasicBlock exceptionTarget) {
        super(EXCEPTION_CHECK, new BasicBlock[]{okTarget, exceptionTarget});
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("br " + this.getOkTarget() + ", on_exception " + this.getExceptionTarget());
    }

    public BasicBlock getOkTarget() {
        return this.nextBasicBlocks[0];
    }

    public BasicBlock getExceptionTarget() {
        return this.nextBasicBlocks[1];
    }

    @Override
    public boolean isConditionEquivalent(CodeUnit s) {
        return s instanceof ExceptionCheckStmt;
    }

    @Override
    public Stmt copy() {
        return new ExceptionCheckStmt(this.getOkTarget(), this.getExceptionTarget());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileExceptionBr(this);
    }

}
