package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.objectweb.asm.Type;

public class PutFieldStmt extends Stmt {
    private final MethodOrFieldIdentifier target;
    private Expr instance;
    private Expr value;

    public PutFieldStmt(MethodOrFieldIdentifier target, Expr instance, Expr value) {
        super(PUT_FIELD);

        writeAt(instance, 0);
        writeAt(value, 1);

        this.target = target;
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.instance = read(0);
        } else if (ptr == 1) {
            this.value = read(1);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getInstance() {
        return instance;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("((" + target.getOwner() + ") ");

        this.instance.toString(printer);

        printer.print(")." + target.getName() + "@" + target.getDesc() + " = ");

        this.value.toString(printer);

        printer.print(';');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof PutFieldStmt && ((PutFieldStmt) s).target.equals(this.target) && ((PutFieldStmt) s).value.equivalent(this.value) && ((PutFieldStmt) s).instance.equivalent(this.instance);
    }

    @Override
    public Stmt copy() {
        return new PutFieldStmt(this.target, this.instance.copy(), this.value.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compilePutField(this);
    }

    public MethodOrFieldIdentifier getTarget() {
        return target;
    }

    public Expr getValue() {
        return value;
    }
}
