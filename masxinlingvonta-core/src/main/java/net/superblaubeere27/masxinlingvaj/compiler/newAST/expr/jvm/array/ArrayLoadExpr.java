package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.utils.LLVMIntrinsic;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class ArrayLoadExpr extends Expr {
    private final JNIType type;

    private Expr array;
    private Expr index;

    public ArrayLoadExpr(JNIType type, Expr array, Expr index) {
        super(ARRAY_LOAD);

        this.type = type;

        writeAt(array, 0);
        writeAt(index, 1);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.array = read(0);
        } else if (ptr == 1) {
            this.index = read(1);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new ArrayLoadExpr(this.type, this.array.copy(), this.index.copy());
    }

    @Override
    public void toString(TabbedStringWriter writer) {
        this.array.toString(writer);

        writer.print("[");

        this.index.toString(writer);

        writer.print("] (" + this.type.getDisplayName() + "[])");
    }

    public Expr getArray() {
        return array;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (!(s instanceof ArrayLoadExpr)) {
            return false;
        }

        var other = ((ArrayLoadExpr) s);

        return other.array.equivalent(this.array) && other.index.equivalent(this.index);
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJNIType(this.type);
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.singletonList(ReadsMemoryProperty.INSTANCE));
    }

    private JNIEnv.JNIEnvMethod getJNIMethod() {
        switch (this.type) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.GetBooleanArrayRegion;
            case CHAR:
                return JNIEnv.JNIEnvMethod.GetCharArrayRegion;
            case BYTE:
                return JNIEnv.JNIEnvMethod.GetByteArrayRegion;
            case SHORT:
                return JNIEnv.JNIEnvMethod.GetShortArrayRegion;
            case INT:
                return JNIEnv.JNIEnvMethod.GetIntArrayRegion;
            case LONG:
                return JNIEnv.JNIEnvMethod.GetLongArrayRegion;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.GetFloatArrayRegion;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.GetDoubleArrayRegion;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var arrayValue = this.array.compile(ctx);
        var indexValue = this.index.compile(ctx);

        // Object arrays need another kind of method to extract the contents of it
        if (this.type == JNIType.OBJECT) {
            return ctx.callEnvironmentMethod(
                    JNIEnv.JNIEnvMethod.GetObjectArrayElement,
                    arrayValue,
                    indexValue
            );
        } else {
            var jniMethod = getJNIMethod();
            var builder = ctx.getBuilder();

            // j<Type> outputValue;
            var outputValue = ctx.getAllocaFor(this.type);
            var bitCastedOutputValue = LLVM.LLVMBuildBitCast(builder,
                    outputValue,
                    LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0),
                    "");

            var size = this.type.getSizeInBytes();

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    ctx.getCompiler(),
                    builder,
                    LLVMIntrinsic.LIFETIME_START,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), size, 1),
                    bitCastedOutputValue
            );

            // Call the Get<PrimitiveType>ArrayRegion method
            ctx.callEnvironmentMethod(
                    jniMethod,
                    arrayValue,
                    indexValue,
                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 0),
                    outputValue
            );

            var retrievedObject = LLVM.LLVMBuildLoad(builder, outputValue, "");

            // Call the llvm.lifetime.end intrinsic
            LLVMUtils.generateIntrinsicCall(
                    ctx.getCompiler(),
                    builder,
                    LLVMIntrinsic.LIFETIME_END,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), size, 1),
                    bitCastedOutputValue
            );

            return ctx.fixTypeOut(retrievedObject, this.type, this.type.getStackStorageType());
        }
    }
}
