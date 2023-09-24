package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.LocksProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.StmtMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

import java.util.Collections;

public class MonitorStmt extends Stmt {
    private final MonitorType type;
    private Expr object;

    public MonitorStmt(MonitorType type, Expr object) {
        super(MONITOR);
        this.type = type;

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

    public MonitorType getType() {
        return type;
    }

    public Expr getObject() {
        return object;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print((this.type == MonitorType.ENTER ? "monitorenter" : "monitorexit") + " " + this.object + ";");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof MonitorStmt && ((MonitorStmt) s).object.equivalent(this.object);
    }

    @Override
    public Stmt copy() {
        return new MonitorStmt(type, this.object.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileMonitorStmt(this);
    }

    public StmtMetadata getMetadata() {
        return new StmtMetadata(Collections.singletonList(new LocksProperty()));
    }

    public enum MonitorType {
        ENTER,
        EXIT
    }
}
