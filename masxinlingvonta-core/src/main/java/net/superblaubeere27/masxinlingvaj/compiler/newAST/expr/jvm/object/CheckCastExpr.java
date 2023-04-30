package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ThrowsProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import javax.naming.OperationNotSupportedException;
import java.util.Collections;

public class CheckCastExpr extends Expr {
    private final String type;
    private Expr instance;

    public CheckCastExpr(String type, Expr instance) {
        super(CHECKCAST);

        this.type = type;

        writeAt(instance, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("checkcast<" + this.type + ">(");

        this.instance.toString(printer);

        printer.print(")");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof CheckCastExpr && ((CheckCastExpr) s).type.equals(this.type) && ((CheckCastExpr) s).instance.equivalent(this.instance);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.instance = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getInstance() {
        return instance;
    }

    @Override
    public Expr copy() {
        return new CheckCastExpr(this.type, this.instance.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    public String getCheckedType() {
        return this.type;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(new ThrowsProperty(Collections.singletonList("java/lang/ClassCastException"))));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var classToCastTo = ctx.buildFindClass(this.type);

        return LLVM.LLVMBuildCall(ctx.getBuilder(), ctx.getCompiler().getIntrinsicMethods().getCheckCastFunction(), new PointerPointer<>(LLVM.LLVMGetParam(ctx.getLLVMFunction(), 0), this.instance.compile(ctx), classToCastTo), 3, "checkcast");
    }
}
