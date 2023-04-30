package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

public class ThrowStmt extends Stmt {
    private Expr object;

    public ThrowStmt(Expr object) {
        super(MONITOR);

        // Yes, this is redundant
        this.object = object;

        writeAt(object, 0);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.object = read(ptr);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getObject() {
        return object;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("throw " + this.object + ";");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ThrowStmt && ((ThrowStmt) s).object.equivalent(this.object);
    }

    @Override
    public Stmt copy() {
        return new ThrowStmt(this.object.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileThrow(this);
    }
}
