package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsLocalProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class VarExpr extends Expr {

    private Local local;

    public VarExpr(Local local) {
        super(LOCAL_LOAD);

        this.local = local;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        if (this.getBlock() != null) {
            this.getBlock().cfg.getLocals().uses.get(this.local).remove(this);
            this.getBlock().cfg.getLocals().uses.getNonNull(local).add(this);
        }

        this.local = local;
    }

    @Override
    public VarExpr copy() {
        return new VarExpr(local);
    }

    @Override
    public ImmType getType() {
        return this.local.getType();
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
//		printer.print("(" + type + ")" + local.toString());
        printer.print(local.toString());
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (s instanceof VarExpr) {
            VarExpr var = (VarExpr) s;

            return local.equals(var.local);
        }
        return false;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.FIRST, Collections.singletonList(new ReadsLocalProperty(this.local)));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.getLocal(this.local);
    }

    public void refactorLocals(HashMap<Local, Local> localMap) {
        var replacement = localMap.get(this.local);

        if (replacement != null)
            this.local = replacement;
    }
}
