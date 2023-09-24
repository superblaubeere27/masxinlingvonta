package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class ArrayLengthExpr extends Expr {
    private Expr array;

    public ArrayLengthExpr(Expr array) {
        super(ARRAY_LENGTH);

        writeAt(array, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("arraylen(");

        this.array.toString(printer);

        printer.print(')');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ArrayLengthExpr && ((ArrayLengthExpr) s).array.equivalent(this.array);
    }

    public Expr getArray() {
        return array;
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.array = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new ArrayLengthExpr(this.array.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.INT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.GetArrayLength, this.array.compile(ctx));
    }
}
