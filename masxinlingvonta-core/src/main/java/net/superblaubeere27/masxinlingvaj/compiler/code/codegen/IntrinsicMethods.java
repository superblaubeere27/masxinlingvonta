package net.superblaubeere27.masxinlingvaj.compiler.code.codegen;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStructGEP;

public class IntrinsicMethods {
    private final LLVMValueRef checkCastFunction;

    private IntrinsicMethods(LLVMValueRef checkCastFunction) {
        this.checkCastFunction = checkCastFunction;
    }

    public static IntrinsicMethods create(MLVCompiler compiler) {
        return new IntrinsicMethods(createCheckCastFunction(compiler));
    }

    private static LLVMValueRef createCheckCastFunction(MLVCompiler compiler) {
        var fun = LLVM.LLVMAddFunction(compiler.getModule(), "_checkcast", LLVM.LLVMFunctionType(JNIType.OBJECT.getLLVMType(), new PointerPointer<>(LLVM.LLVMPointerType(compiler.getJni().getJniEnv().getType(), 0), JNIType.OBJECT.getLLVMType(), JNIType.OBJECT.getLLVMType()), 3, 0));

        LLVM.LLVMSetLinkage(fun, LLVM.LLVMPrivateLinkage);

        var builder = LLVM.LLVMCreateBuilder();

        var entryBlock = LLVM.LLVMAppendBasicBlock(fun, "entry");
        var successBlock = LLVM.LLVMAppendBasicBlock(fun, "success");
        var errorBlock = LLVM.LLVMAppendBasicBlock(fun, "error");

        LLVM.LLVMPositionBuilderAtEnd(builder, entryBlock);

        {
            var nullBlock = LLVM.LLVMAppendBasicBlock(fun, "isNull");
            var notNullBlock = LLVM.LLVMAppendBasicBlock(fun, "notNull");

            LLVM.LLVMBuildCondBr(builder, LLVM.LLVMBuildIsNull(builder, LLVM.LLVMGetParam(fun, 1), "nullCheck"), nullBlock, notNullBlock);

            LLVM.LLVMPositionBuilderAtEnd(builder, nullBlock);

            LLVM.LLVMBuildRet(builder, LLVM.LLVMGetParam(fun, 1));

            entryBlock = notNullBlock;

            LLVM.LLVMPositionBuilderAtEnd(builder, entryBlock);
        }

        var functionTable = LLVMBuildLoad(builder,
                LLVMBuildStructGEP(builder, LLVM.LLVMGetParam(fun, 0), 0, ""),
                "function_table");

        LLVMValueRef objectClass = compiler.getJni().getJniEnv().callEnvironmentMethod(LLVM.LLVMGetParam(fun, 0), JNIEnv.JNIEnvMethod.GetObjectClass, builder, functionTable, LLVM.LLVMGetParam(fun, 1));

        LLVMValueRef successFlag = compiler.getJni().getJniEnv().callEnvironmentMethod(LLVM.LLVMGetParam(fun, 0), JNIEnv.JNIEnvMethod.IsAssignableFrom, builder, functionTable, objectClass, LLVM.LLVMGetParam(fun, 2));

        LLVM.LLVMBuildCondBr(builder, successFlag, successBlock, errorBlock);

        LLVM.LLVMPositionBuilderAtEnd(builder, successBlock);

        // The reference is needed due to the local ref analysis assuming that a check cast will create a new reference
        LLVMValueRef retValue = compiler.getJni().getJniEnv().callEnvironmentMethod(LLVM.LLVMGetParam(fun, 0), JNIEnv.JNIEnvMethod.NewLocalRef, builder, functionTable, LLVM.LLVMGetParam(fun, 1));

        LLVM.LLVMBuildRet(builder, retValue);

        LLVM.LLVMPositionBuilderAtEnd(builder, errorBlock);

        LLVMValueRef exceptionClass = compiler.getOnLoadBuilder().buildFindClass(builder, "java/lang/ClassCastException");

        compiler.getJni().getJniEnv().callEnvironmentMethod(LLVM.LLVMGetParam(fun, 0), JNIEnv.JNIEnvMethod.ThrowNew, builder, functionTable, exceptionClass, LLVM.LLVMBuildGlobalStringPtr(builder, "??? cannot be casted to ???", "exceptionMsg"));

        LLVM.LLVMBuildRet(builder, LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType()));

        return fun;
    }

    /**
     * Returns the checkcast compiler intrinsic function
     * <p>
     * SIGNATURE:
     * <p>
     * `jobject checkcast(JNIEnv* env, jobject obj, jclass classToCastTo)`
     *
     * @return the reference to the llvm function
     */
    public LLVMValueRef getCheckCastFunction() {
        return checkCastFunction;
    }
}
