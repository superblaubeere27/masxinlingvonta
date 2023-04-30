package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.objectweb.asm.Type;

import java.util.Collections;

public class GetFieldExpr extends LoadFieldExpr {
    private Expr instance;

    public GetFieldExpr(MethodOrFieldIdentifier target, Expr instance) {
        super(GET_FIELD, target);

        writeAt(instance, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("((" + target.getOwner() + ") ");

        this.instance.toString(printer);

        printer.print(")." + target.getName() + "@" + target.getDesc());
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof GetFieldExpr && ((GetFieldExpr) s).target.equals(this.target) && ((GetFieldExpr) s).instance.equivalent(s);
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
        return new GetFieldExpr(this.target, this.instance.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJVMType(Type.getType(this.target.getDesc()));
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(ReadsMemoryProperty.INSTANCE));
    }

    private JNIEnv.JNIEnvMethod getUsedJNIMethod(FunctionCodegenContext ctx) {
        JNIType type = ctx.getCompiler().getJni().toNativeType(Type.getType(this.target.getDesc()));

        switch (type) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.GetBooleanField;
            case CHAR:
                return JNIEnv.JNIEnvMethod.GetCharField;
            case BYTE:
                return JNIEnv.JNIEnvMethod.GetByteField;
            case SHORT:
                return JNIEnv.JNIEnvMethod.GetShortField;
            case INT:
                return JNIEnv.JNIEnvMethod.GetIntField;
            case LONG:
                return JNIEnv.JNIEnvMethod.GetLongField;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.GetFloatField;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.GetDoubleField;
            case OBJECT:
                return JNIEnv.JNIEnvMethod.GetObjectField;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var classId = ctx.buildFindClass(this.target.getOwner());
        var fieldId = ctx.buildGetFieldID(this.target, classId, false);

        JNIType nativeType = this.getType().toNativeType();

        return ctx.fixTypeOut(ctx.callEnvironmentMethod(getUsedJNIMethod(ctx), this.instance.compile(ctx), fieldId), nativeType, nativeType.getStackStorageType());
    }
}
