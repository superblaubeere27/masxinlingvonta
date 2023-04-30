package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsExceptionStateProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.objectweb.asm.Type;

import java.util.Arrays;

public class GetStaticExpr extends LoadFieldExpr {

    public GetStaticExpr(MethodOrFieldIdentifier target) {
        super(GET_STATIC_FIELD, target);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(target.getOwner() + "::" + target.getName() + "@" + target.getDesc());
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof GetStaticExpr && ((GetStaticExpr) s).target.equals(this.target);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new GetStaticExpr(this.target);
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJVMType(Type.getType(this.target.getDesc()));
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Arrays.asList(ReadsExceptionStateProperty.INSTANCE));
    }

    private JNIEnv.JNIEnvMethod getUsedJNIMethod(FunctionCodegenContext ctx) {
        JNIType type = ctx.getCompiler().getJni().toNativeType(Type.getType(this.target.getDesc()));

        switch (type) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.GetStaticBooleanField;
            case CHAR:
                return JNIEnv.JNIEnvMethod.GetStaticCharField;
            case BYTE:
                return JNIEnv.JNIEnvMethod.GetStaticByteField;
            case SHORT:
                return JNIEnv.JNIEnvMethod.GetStaticShortField;
            case INT:
                return JNIEnv.JNIEnvMethod.GetStaticIntField;
            case LONG:
                return JNIEnv.JNIEnvMethod.GetStaticLongField;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.GetStaticFloatField;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.GetStaticDoubleField;
            case OBJECT:
                return JNIEnv.JNIEnvMethod.GetStaticObjectField;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var classId = ctx.buildFindClass(this.target.getOwner());
        var fieldId = ctx.buildGetFieldID(this.target, classId, true);

        JNIType nativeType = this.getType().toNativeType();

        return ctx.fixTypeOut(ctx.callEnvironmentMethod(getUsedJNIMethod(ctx), classId, fieldId), nativeType, nativeType.getStackStorageType());
    }
}
