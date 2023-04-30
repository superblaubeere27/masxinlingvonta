package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class InstanceOfExpr extends Expr {
    private final String type;
    private Expr instance;

    public InstanceOfExpr(String type, Expr instance) {
        super(INSTANCEOF);

        this.type = type;

        writeAt(instance, 0);
    }

    public Expr getInstance() {
        return instance;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("instanceof<" + this.type + ">(");

        this.instance.toString(printer);

        printer.print(")");
    }

    public String getInstanceOfType() {
        return this.type;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof InstanceOfExpr && ((InstanceOfExpr) s).type.equals(this.type) && ((InstanceOfExpr) s).instance.equivalent(this.instance);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.instance = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new InstanceOfExpr(this.type, this.instance.copy());
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
        var obj = this.instance.compile(ctx);
        var classId = ctx.buildFindClass(this.type);

        return ctx.fixTypeOut(ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.IsInstanceOf, obj, classId), JNIType.BOOLEAN, JNIType.BOOLEAN.getStackStorageType());
    }
}
