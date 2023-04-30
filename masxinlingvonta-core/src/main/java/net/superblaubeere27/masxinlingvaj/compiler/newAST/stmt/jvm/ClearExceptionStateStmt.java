package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public class ClearExceptionStateStmt extends Stmt {

    public ClearExceptionStateStmt() {
        super(CLEAR_EXCEPTION);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("clear_exception;");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ClearExceptionStateStmt;
    }

    @Override
    public Stmt copy() {
        return new ClearExceptionStateStmt();
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileClearExceptionState(this);
    }
}
