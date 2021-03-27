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

public class FieldInstruction extends Instruction {
    private final MethodOrFieldIdentifier target;
    private final StackSlot instance;
    private final StackSlot stackTarget;
    private final JNIType targetType;
    private final boolean store;

    public FieldInstruction(MethodOrFieldIdentifier target, StackSlot instance, StackSlot stackTarget, JNIType targetType, boolean store) {
        this.target = target;
        this.instance = instance;
        this.stackTarget = stackTarget;
        this.targetType = targetType;
        this.store = store;
    }

    private static JNIEnv.JNIEnvMethod getJNIMethod(JNIType jniType, boolean store) {
        if (store) {
            switch (jniType) {
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
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        } else {
            switch (jniType) {
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

        var field = translatedMethod.buildGetFieldID(compiler.getJni(), this.target, classId, false);

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        var jniMethod = getJNIMethod(compiler.getJni().toNativeType(Type.getType(this.target.getDesc())), this.store);

        if (this.store) {
            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.instance),
                    field,
                    translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                            this.stackTarget,
                            this.targetType)
            );
        } else {
            var retrievedField = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    jniMethod,
                    translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.instance),
                    field
            );

            translatedMethod.getStack().buildStackStore(translatedMethod.getLlvmBuilder(),
                    this.stackTarget,
                    retrievedField,
                    true);
        }

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }
}
