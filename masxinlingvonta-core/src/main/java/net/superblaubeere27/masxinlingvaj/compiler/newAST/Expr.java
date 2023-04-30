package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstIntExpr;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Set;

public abstract class Expr extends CodeUnit {
    protected CodeUnit parent;

    public Expr(int opcode) {
        super(opcode);
    }

    public static String typesToString(Expr[] a) {
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(a[i] == null ? "NULL" : a[i].getType());
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    @Override
    public abstract void onChildUpdated(int ptr);

    @Override
    public abstract Expr copy();

    public abstract ImmType getType();

    public abstract ExprMetadata getMetadata();

    public abstract LLVMValueRef compile(FunctionCodegenContext ctx);

    public CodeUnit getParent() {
        return parent;
    }

    protected final void setParent(CodeUnit parent) {
        this.parent = parent;

        if (parent != null) {
            setBlock(parent.getBlock());
        } else {
            setBlock(null);
        }
    }

    public void unlink() {
        if (parent != null) {
            parent.deleteAt(parent.indexOf(this));
        }
    }

    public Stmt getRootParent() {
        CodeUnit p = parent;
        if (p == null) {
            /* expressions must have a parent. */
            // except for phi args?
//			throw new UnsupportedOperationException("We've found a dangler, " + id + ". " + this);
            return null;
        } else {
            if ((p.flags & FLAG_STMT) != 0) {
                return (Stmt) p;
            } else {
                return ((Expr) p).getRootParent();
            }
        }
    }

    public Iterable<Expr> enumerateWithSelf() {
        Set<Expr> set = _enumerate();
        set.add(this);
        return set;
    }

    public void verify() {
        for (Expr child : children) {
            if (child == null || child instanceof ConstExpr || child instanceof VarExpr)
                continue;

            throw new IllegalStateException("Invalid expression child " + child);
        }
    }
}
