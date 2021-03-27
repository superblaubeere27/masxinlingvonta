package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.MethodStack;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.utils.LLVMIntrinsic;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

public class ArrayModificationInstruction extends Instruction {
    private final StackSlot arraySlot;
    private final StackSlot indexSlot;
    private final StackSlot valueSlot;
    private final boolean store;

    public ArrayModificationInstruction(StackSlot arraySlot, StackSlot indexSlot, StackSlot valueSlot, boolean store) {
        if (indexSlot.getType() != JNIType.INT)
            throw new IllegalStateException("An array index has to be an int.");

        this.arraySlot = arraySlot;
        this.indexSlot = indexSlot;
        this.valueSlot = valueSlot;
        this.store = store;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var stack = translatedMethod.getStack();

        var array = stack.buildStackLoad(builder, this.arraySlot);
        var index = stack.buildStackLoad(builder, this.indexSlot);

        var valueType = this.valueSlot.getType();

        if (this.store) {
            buildArrayStore(compiler, translatedMethod, block, builder, stack, array, index, valueType);
        } else {
            buildArrayLoad(compiler, translatedMethod, block, builder, stack, array, index, valueType);
        }

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }

    private void buildArrayStore(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block, LLVMBuilderRef builder, MethodStack stack, LLVMValueRef array, LLVMValueRef index, JNIType valueType) {
        var value = stack.buildStackLoad(builder, this.valueSlot);

        // Object arrays need another kind of method to extract the contents of it
        if (valueType == JNIType.OBJECT) {
            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    JNIEnv.JNIEnvMethod.SetObjectArrayElement,
                    array,
                    index,
                    value
            );
        } else {
            var jniMethod = getJNIMethod(valueType, true);

            // j<Type> outputValue;
            var inputValue = LLVM.LLVMBuildAlloca(builder, valueType.getLLVMType(), "stackAlloc");
            var bitCastedOutputValue = LLVM.LLVMBuildBitCast(builder,
                    inputValue,
                    LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0),
                    "");

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_START,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedOutputValue
            );

            // Store the value in the allocated space
            LLVM.LLVMBuildStore(builder, value, inputValue);

            // Call the Get<PrimitiveType>ArrayRegion method
            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    array,
                    index,
                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 0),
                    inputValue
            );

            // Call the llvm.lifetime.end intrinsic
            LLVMUtils.generateIntrinsicCall(
                    compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_END,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedOutputValue
            );
        }
    }

    private void buildArrayLoad(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block, LLVMBuilderRef builder, MethodStack stack, LLVMValueRef array, LLVMValueRef index, JNIType valueType) {
        // Object arrays need another kind of method to extract the contents of it
        if (valueType == JNIType.OBJECT) {
            var retrievedObject = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    JNIEnv.JNIEnvMethod.GetObjectArrayElement,
                    array,
                    index
            );

            stack.buildStackStore(builder, this.valueSlot, retrievedObject, true);
        } else {
            var jniMethod = getJNIMethod(valueType, false);

            // j<Type> outputValue;
            var outputValue = LLVM.LLVMBuildAlloca(builder, valueType.getLLVMType(), "stackAlloc");
            var bitCastedOutputValue = LLVM.LLVMBuildBitCast(builder,
                    outputValue,
                    LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0),
                    "");

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_START,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedOutputValue
            );

            // Call the Get<PrimitiveType>ArrayRegion method
            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    array,
                    index,
                    LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 1, 0),
                    outputValue
            );

            var retrievedObject = LLVM.LLVMBuildLoad(builder, outputValue, "");

            // Call the llvm.lifetime.end intrinsic
            LLVMUtils.generateIntrinsicCall(
                    compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_END,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedOutputValue
            );

            stack.buildStackStore(builder, this.valueSlot, retrievedObject, true);
        }
    }

    /**
     * Determine which JNI method should be used to access the array elements
     */
    private JNIEnv.JNIEnvMethod getJNIMethod(JNIType type, boolean store) {
        if (store) {
            switch (type) {
                case BOOLEAN:
                    return JNIEnv.JNIEnvMethod.SetBooleanArrayRegion;
                case CHAR:
                    return JNIEnv.JNIEnvMethod.SetCharArrayRegion;
                case BYTE:
                    return JNIEnv.JNIEnvMethod.SetByteArrayRegion;
                case SHORT:
                    return JNIEnv.JNIEnvMethod.SetShortArrayRegion;
                case INT:
                    return JNIEnv.JNIEnvMethod.SetIntArrayRegion;
                case LONG:
                    return JNIEnv.JNIEnvMethod.SetLongArrayRegion;
                case FLOAT:
                    return JNIEnv.JNIEnvMethod.SetFloatArrayRegion;
                case DOUBLE:
                    return JNIEnv.JNIEnvMethod.SetDoubleArrayRegion;
                default:
                    throw new IllegalStateException("Unexpected value: " + type);
            }
        } else {
            switch (type) {
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
    }
}
