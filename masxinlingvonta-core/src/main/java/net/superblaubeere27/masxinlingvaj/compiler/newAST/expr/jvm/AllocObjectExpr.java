package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.AllocatesProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class AllocObjectExpr extends Expr {
    private final String type;

    public AllocObjectExpr(String type) {
        super(ALLOC_OBJECT);

        this.type = type;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("alloc<" + this.type + ">()");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof AllocObjectExpr && ((AllocObjectExpr) s).type.equals(this.type);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new AllocObjectExpr(this.type);
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    public String getAllocatedType() {
        return this.type;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(AllocatesProperty.INSTANCE));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.AllocObject, ctx.buildFindClass(this.type));
    }
}
