package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class CreateLocalRefExpr extends Expr {
    private Expr instance;

    public CreateLocalRefExpr(Expr instance) {
        super(CREATE_REF);

        assert instance.getType() == ImmType.OBJECT;

        writeAt(instance, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("copy_ref(");

        this.instance.toString(printer);

        printer.print(')');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof CreateLocalRefExpr && ((CreateLocalRefExpr) s).instance.equivalent(s);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.instance = read(ptr);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new CreateLocalRefExpr(this.instance.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.NewLocalRef, this.instance.compile(ctx));
    }
}
