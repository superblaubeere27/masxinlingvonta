package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.objectweb.asm.Type;

public class StaticFieldInstruction extends Instruction {
    private final MethodOrFieldIdentifier target;
    private final StackSlot stackTarget;
    private final JNIType targetType;
    private final boolean store;

    public StaticFieldInstruction(MethodOrFieldIdentifier target, StackSlot stackTarget, JNIType targetType, boolean store) {
        this.target = target;
        this.stackTarget = stackTarget;
        this.targetType = targetType;
        this.store = store;
    }

    private static JNIEnv.JNIEnvMethod getJNIMethod(JNIType jniType, boolean store) {
        if (store) {
            switch (jniType) {
                case BOOLEAN:
                    return JNIEnv.JNIEnvMethod.SetStaticBooleanField;
                case CHAR:
                    return JNIEnv.JNIEnvMethod.SetStaticCharField;
                case BYTE:
                    return JNIEnv.JNIEnvMethod.SetStaticByteField;
                case SHORT:
                    return JNIEnv.JNIEnvMethod.SetStaticShortField;
                case INT:
                    return JNIEnv.JNIEnvMethod.SetStaticIntField;
                case LONG:
                    return JNIEnv.JNIEnvMethod.SetStaticLongField;
                case FLOAT:
                    return JNIEnv.JNIEnvMethod.SetStaticFloatField;
                case DOUBLE:
                    return JNIEnv.JNIEnvMethod.SetStaticDoubleField;
                case OBJECT:
                    return JNIEnv.JNIEnvMethod.SetStaticObjectField;
                default:
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        } else {
            switch (jniType) {
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
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        }
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var classId = translatedMethod.buildFindClass(compiler.getJni(), this.target.getOwner());

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        var field = translatedMethod.buildGetFieldID(compiler.getJni(), this.target, classId, true);

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        var jniMethod = getJNIMethod(compiler.getJni().toNativeType(Type.getType(this.target.getDesc())), this.store);

        if (this.store) {
            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    classId,
                    field,
                    translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                            this.stackTarget,
                            this.targetType)
            );
        } else {
            var retrievedField = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    classId,
                    field
            );

            translatedMethod.getStack().buildStackStore(translatedMethod.getLlvmBuilder(),
                    this.stackTarget,
                    retrievedField);
        }

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }

}
