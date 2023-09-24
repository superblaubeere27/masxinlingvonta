package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.WritesMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.StmtMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.Collections;

public class PutStaticStmt extends Stmt {
    private final MethodOrFieldIdentifier target;
    private Expr value;

    public PutStaticStmt(MethodOrFieldIdentifier target, Expr value) {
        super(PUT_STATIC_FIELD);

        writeAt(value, 0);

        this.target = target;
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.value = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(target.getOwner() + "::" + target.getName() + "@" + target.getDesc() + " = ");

        this.value.toString(printer);

        printer.print(';');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof PutStaticStmt && ((PutStaticStmt) s).target.equals(this.target) && ((PutStaticStmt) s).value.equivalent(this.value);
    }

    public StmtMetadata getMetadata() {
        return new StmtMetadata(Collections.singletonList(new WritesMemoryProperty(this.target)));
    }

    public Expr getValue() {
        return value;
    }

    public MethodOrFieldIdentifier getTarget() {
        return target;
    }

    @Override
    public Stmt copy() {
        return new PutStaticStmt(this.target, this.value.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compilePutStaticField(this);
    }
}
