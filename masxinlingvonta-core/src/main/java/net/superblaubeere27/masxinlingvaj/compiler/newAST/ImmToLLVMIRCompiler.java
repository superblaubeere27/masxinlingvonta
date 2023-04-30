package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.SwitchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.*;
import net.superblaubeere27.masxinlingvaj.utils.LLVMIntrinsic;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;

public class ImmToLLVMIRCompiler {
    private final FunctionCodegenContext ctx;

    public ImmToLLVMIRCompiler(FunctionCodegenContext ctx) {
        this.ctx = ctx;
    }

    public void compileCondBr(ConditionalBranch condBr) {
        LLVM.LLVMBuildCondBr(
                ctx.getBuilder(),
                condBr.getCond().compile(ctx),
                ctx.getLLVMBlock(condBr.getIfTarget()),
                ctx.getLLVMBlock(condBr.getElseTarget())
        );
    }

    public void compileExceptionBr(ExceptionCheckStmt exBr) {
        LLVM.LLVMBuildCondBr(
                ctx.getBuilder(),
                ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.ExceptionCheck),
                ctx.getLLVMBlock(exBr.getExceptionTarget()),
                ctx.getLLVMBlock(exBr.getOkTarget())
        );
    }

    public void compileSwitch(SwitchStmt switchStmt) {
        var builtSwitch = LLVM.LLVMBuildSwitch(
                ctx.getBuilder(),
                switchStmt.getOperand().compile(ctx),
                ctx.getLLVMBlock(switchStmt.getDefaultBlock()),
                switchStmt.getNextBasicBlocks().length - 1
        );

        for (int i = 0; i < switchStmt.getKeys().length; i++) {
            var key = switchStmt.getKeys()[i];
            var block = ctx.getLLVMBlock(switchStmt.getNextBasicBlocks()[i]);

            LLVM.LLVMAddCase(builtSwitch, LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), key, 1), block);
        }
    }

    public void compileBr(UnconditionalBranch br) {
        LLVM.LLVMBuildBr(ctx.getBuilder(), ctx.getLLVMBlock(br.getTarget()));
    }

    public void compileCopyStmt(AbstractCopyStmt copyStmt) {
        var expr = copyStmt.getExpression().compile(ctx);

        ctx.putLocal(copyStmt.getVariable().getLocal(), expr);
    }

    public void compileArrayStore(ArrayStoreStmt arrayStoreStmt) {
        var arrayValue = arrayStoreStmt.getArray().compile(ctx);
        var indexValue = arrayStoreStmt.getIndex().compile(ctx);

        var elementValue = ctx.fixTypeIn(arrayStoreStmt.getElement().compile(ctx), arrayStoreStmt.getType(), false);

        // Object arrays need another kind of method to extract the contents of it
        if (arrayStoreStmt.getType() == JNIType.OBJECT) {
            ctx.callEnvironmentMethod(
                    JNIEnv.JNIEnvMethod.SetObjectArrayElement,
                    arrayValue,
                    indexValue,
                    elementValue
            );
        } else {
            var jniMethod = arrayStoreStmt.getJNIMethod();
            var builder = ctx.getBuilder();

            // j<Type> outputValue;
            var inputValue = ctx.getAllocaFor(arrayStoreStmt.getType());
            var bitCastedOutputValue = LLVM.LLVMBuildBitCast(builder,
                    inputValue,
                    LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0),
                    "");

            var size = arrayStoreStmt.getType().getSizeInBytes();

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    ctx.getCompiler(),
                    builder,
                    LLVMIntrinsic.LIFETIME_START,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), size, 1),
                    bitCastedOutputValue
            );

            // Store the value in the allocated space
            LLVM.LLVMBuildStore(builder, elementValue, inputValue);

            // Call the Get<PrimitiveType>ArrayRegion method
            ctx.callEnvironmentMethod(
                    jniMethod,
                    arrayValue,
                    indexValue,
                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 0),
                    inputValue
            );

            // Call the llvm.lifetime.end intrinsic
            LLVMUtils.generateIntrinsicCall(
                    ctx.getCompiler(),
                    builder,
                    LLVMIntrinsic.LIFETIME_END,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), size, 1),
                    bitCastedOutputValue
            );
        }
    }

    public void compileClearExceptionState(ClearExceptionStateStmt stmt) {
        ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.ExceptionClear);
    }

    public void compileDeleteRef(DeleteRefStmt stmt) {
        ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.DeleteLocalRef, stmt.getObject().compile(ctx));
    }

    public void compileMonitorStmt(MonitorStmt stmt) {
        JNIEnv.JNIEnvMethod method = stmt.getType() == MonitorStmt.MonitorType.ENTER ? JNIEnv.JNIEnvMethod.MonitorEnter : JNIEnv.JNIEnvMethod.MonitorExit;

        ctx.callEnvironmentMethod(method, stmt.getObject().compile(ctx));
    }

    public void compilePutField(PutFieldStmt stmt) {
        var classId = ctx.buildFindClass(stmt.getTarget().getOwner());
        var fieldId = ctx.buildGetFieldID(stmt.getTarget(), classId, false);

        var type = ctx.getCompiler().getJni().toNativeType(Type.getType(stmt.getTarget().getDesc()));

        ctx.callEnvironmentMethod(this.getPutFieldJNIMethod(type), stmt.getInstance().compile(ctx), fieldId, ctx.fixTypeIn(stmt.getValue().compile(ctx), type, false));
    }

    public JNIEnv.JNIEnvMethod getPutFieldJNIMethod(JNIType type) {
        switch (type) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.SetBooleanField;
            case CHAR:
                return JNIEnv.JNIEnvMethod.SetCharField;
            case BYTE:
                return JNIEnv.JNIEnvMethod.SetByteField;
            case SHORT:
                return JNIEnv.JNIEnvMethod.SetShortField;
            case INT:
                return JNIEnv.JNIEnvMethod.SetIntField;
            case LONG:
                return JNIEnv.JNIEnvMethod.SetLongField;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.SetFloatField;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.SetDoubleField;
            case OBJECT:
                return JNIEnv.JNIEnvMethod.SetObjectField;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public void compilePutStaticField(PutStaticStmt stmt) {
        var classId = ctx.buildFindClass(stmt.getTarget().getOwner());
        var fieldId = ctx.buildGetFieldID(stmt.getTarget(), classId, true);

        var jniType = ctx.getCompiler().getJni().toNativeType(Type.getType(stmt.getTarget().getDesc()));

        ctx.callEnvironmentMethod(getPutStaticFieldJNIMethod(jniType), classId, fieldId, ctx.fixTypeIn(stmt.getValue().compile(ctx), jniType, false));
    }

    private JNIEnv.JNIEnvMethod getPutStaticFieldJNIMethod(JNIType type) {
        return switch (type) {
            case BOOLEAN -> JNIEnv.JNIEnvMethod.SetStaticBooleanField;
            case CHAR -> JNIEnv.JNIEnvMethod.SetStaticCharField;
            case BYTE -> JNIEnv.JNIEnvMethod.SetStaticByteField;
            case SHORT -> JNIEnv.JNIEnvMethod.SetStaticShortField;
            case INT -> JNIEnv.JNIEnvMethod.SetStaticIntField;
            case LONG -> JNIEnv.JNIEnvMethod.SetStaticLongField;
            case FLOAT -> JNIEnv.JNIEnvMethod.SetStaticFloatField;
            case DOUBLE -> JNIEnv.JNIEnvMethod.SetStaticDoubleField;
            case OBJECT -> JNIEnv.JNIEnvMethod.SetStaticObjectField;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    public void compileThrow(ThrowStmt stmt) {
        ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.Throw, stmt.getObject().compile(ctx));
    }

    public void compileExpressionStmt(ExpressionStmt stmt) {
        stmt.getExpression().compile(ctx);
    }

    public void compileRet(RetStmt stmt) {
        var returnType = ctx.toNativeType(Type.getReturnType(ctx.getCfg().getCompilerMethod().getNode().desc));

        var compiledVal = stmt.getValue().compile(ctx);

        if (stmt.getValue().getType() == ImmType.INT && returnType != JNIType.INT) {
            compiledVal = LLVM.LLVMBuildIntCast(ctx.getBuilder(),
                    compiledVal,
                    returnType.getLLVMType(),
                    "cst");
        }

        // Not much to do here...
        LLVM.LLVMBuildRet(ctx.getBuilder(), compiledVal);
    }

    public void compileRetVoid(RetVoidStmt stmt) {
        LLVMBuildRetVoid(ctx.getBuilder());
    }
}
