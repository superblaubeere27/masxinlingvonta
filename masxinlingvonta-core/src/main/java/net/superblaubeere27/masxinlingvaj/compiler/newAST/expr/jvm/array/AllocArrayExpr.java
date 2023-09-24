package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.AllocatesProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

import java.util.Collections;

import static org.objectweb.asm.Type.*;

public class AllocArrayExpr extends Expr {
    private final Type arrayType;
    private Expr count;

    public AllocArrayExpr(Type arrayType, Expr count) {
        super(ALLOC_ARRAY);

        this.arrayType = arrayType;

        writeAt(count, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("alloc_array<" + this.arrayType + ">(");

        this.count.toString(printer);

        printer.print(")");
    }

    public Expr getCount() {
        return count;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof AllocArrayExpr && ((AllocArrayExpr) s).arrayType.equals(this.arrayType) && ((AllocArrayExpr) s).count.equivalent(this.count);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.count = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Type getArrayType() {
        return arrayType;
    }

    @Override
    public Expr copy() {
        return new AllocArrayExpr(this.arrayType, this.count.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(AllocatesProperty.INSTANCE));
    }

    private JNIEnv.JNIEnvMethod getJNIMethod() {
        return switch (this.arrayType.getSort()) {
            case BOOLEAN -> JNIEnv.JNIEnvMethod.NewBooleanArray;
            case CHAR -> JNIEnv.JNIEnvMethod.NewCharArray;
            case BYTE -> JNIEnv.JNIEnvMethod.NewByteArray;
            case SHORT -> JNIEnv.JNIEnvMethod.NewShortArray;
            case INT -> JNIEnv.JNIEnvMethod.NewIntArray;
            case FLOAT -> JNIEnv.JNIEnvMethod.NewFloatArray;
            case LONG -> JNIEnv.JNIEnvMethod.NewLongArray;
            case DOUBLE -> JNIEnv.JNIEnvMethod.NewDoubleArray;
            default -> throw new IllegalStateException("Unexpected value: " + this.arrayType);
        };
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var length = this.count.compile(ctx);

        if (this.arrayType.getSort() == Type.OBJECT || this.arrayType.getSort() == ARRAY) {
            return ctx.callEnvironmentMethod(
                    JNIEnv.JNIEnvMethod.NewObjectArray,
                    length,
                    ctx.buildFindClass(this.arrayType.getInternalName()),
                    LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType())
            );
        } else {
            var jniMethod = getJNIMethod();

            return ctx.callEnvironmentMethod(
                    jniMethod,
                    length
            );
        }
    }
}
