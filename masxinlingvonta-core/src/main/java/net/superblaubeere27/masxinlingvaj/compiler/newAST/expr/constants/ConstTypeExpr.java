package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsExceptionStateProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ThrowsProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.objectweb.asm.Type;

import java.util.Collections;

public class ConstTypeExpr extends ConstExpr {
    private final Type value;

    public ConstTypeExpr(Type value) {
        super(CONST_TYPE);

        this.value = value;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(this.value.toString());
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ConstTypeExpr && ((ConstTypeExpr) s).value.equals(this.value);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new ConstTypeExpr(this.value);
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(ThrowsProperty.INSTANCE));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.NewLocalRef, ctx.buildFindClass(value.getInternalName()));
    }
}
