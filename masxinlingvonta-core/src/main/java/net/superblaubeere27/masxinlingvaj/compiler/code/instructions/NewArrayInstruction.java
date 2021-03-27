package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Type.*;

public class NewArrayInstruction extends Instruction {
    private final Type type;
    private final StackSlot length;
    private final StackSlot output;

    public NewArrayInstruction(Type type, StackSlot length, StackSlot output) {
        this.type = type;
        this.length = length;
        this.output = output;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var stack = translatedMethod.getStack();

        var length = stack.buildStackLoad(builder, this.length);

        LLVMValueRef array;

        if (this.type.getSort() == Type.OBJECT) {
            array = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    JNIEnv.JNIEnvMethod.NewObjectArray,
                    length,
                    translatedMethod.buildFindClass(compiler.getJni(), this.type.getInternalName()),
                    LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType())
            );
        } else {
            var jniMethod = getJNIMethod();

            array = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    length
            );
        }

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        // Store the created array in the output slot
        stack.buildStackStore(builder, this.output, array);
    }

    private JNIEnv.JNIEnvMethod getJNIMethod() {
        switch (this.type.getSort()) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.NewBooleanArray;
            case CHAR:
                return JNIEnv.JNIEnvMethod.NewCharArray;
            case BYTE:
                return JNIEnv.JNIEnvMethod.NewByteArray;
            case SHORT:
                return JNIEnv.JNIEnvMethod.NewShortArray;
            case INT:
                return JNIEnv.JNIEnvMethod.NewIntArray;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.NewFloatArray;
            case LONG:
                return JNIEnv.JNIEnvMethod.NewLongArray;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.NewDoubleArray;
            default:
                throw new IllegalStateException("Unexpected value: " + this.type.getSort());
        }
    }
}
