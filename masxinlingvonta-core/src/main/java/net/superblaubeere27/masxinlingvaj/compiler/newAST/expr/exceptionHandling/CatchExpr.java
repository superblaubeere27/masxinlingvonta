package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsExceptionStateProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class CatchExpr extends Expr {

    public CatchExpr() {
        super(CATCH);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("catch()");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s.getOpcode() == CATCH;
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new CatchExpr();
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(ReadsExceptionStateProperty.INSTANCE));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.ExceptionOccurred);
    }
}
