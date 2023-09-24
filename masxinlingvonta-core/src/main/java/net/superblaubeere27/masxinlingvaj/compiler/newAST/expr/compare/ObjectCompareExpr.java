package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstNullExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

/**
 * Checks whether objects are equal
 */
public class ObjectCompareExpr extends Expr {
    private Expr lhs;
    private Expr rhs;

    public ObjectCompareExpr(Expr lhs, Expr rhs) {
        super(OBJECT_COMPARE);

        writeAt(lhs, 0);
        writeAt(rhs, 1);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        this.lhs.toString(printer);

        printer.print(" === ");

        this.rhs.toString(printer);
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ObjectCompareExpr && ((ObjectCompareExpr) s).lhs.equivalent(this.lhs) && ((ObjectCompareExpr) s).rhs.equivalent(this.rhs);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.lhs = this.read(0);
        } else if (ptr == 1) {
            this.rhs = this.read(1);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public ImmType getType() {
        return ImmType.BOOL;
    }

    @Override
    public Expr copy() {
        return new ObjectCompareExpr(this.lhs.copy(), this.rhs.copy());
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    public Expr getLhs() {
        return lhs;
    }

    public Expr getRhs() {
        return rhs;
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        Expr first;
        Expr second;

        if (this.lhs instanceof ConstNullExpr) {
            first = this.lhs;
            second = this.rhs;
        } else {
            first = this.rhs;
            second = this.lhs;
        }

        if (first instanceof ConstNullExpr) {
            LLVMValueRef result;

            result = LLVM.LLVMBuildIsNull(ctx.getBuilder(), second.compile(ctx), "");

            return result;
        } else {
            return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.IsSameObject, this.lhs.compile(ctx), this.rhs.compile(ctx));
        }
    }
}
