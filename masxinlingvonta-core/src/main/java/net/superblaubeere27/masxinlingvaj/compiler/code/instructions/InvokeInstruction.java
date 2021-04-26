package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.objectweb.asm.Type;

public class InvokeInstruction extends Instruction {
    private final MethodOrFieldIdentifier target;
    private final StackSlot[] params;
    private final JNIType[] targetTypes;
    private final StackSlot resultTarget;
    private final boolean isStatic;
    private final boolean isNonVirtual;

    public InvokeInstruction(MethodOrFieldIdentifier target, StackSlot[] params, JNIType[] targetTypes, StackSlot resultTarget, boolean isStatic, boolean isNonVirtual) {
        if (params.length == 0 && !isStatic) {
            throw new IllegalArgumentException("No instance parameter?");
        }

        this.target = target;
        this.params = params;
        this.targetTypes = targetTypes;
        this.resultTarget = resultTarget;
        this.isStatic = isStatic;
        this.isNonVirtual = isNonVirtual;
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

        var method = translatedMethod.buildGetMethodID(compiler.getJni(), this.target, classId, this.isStatic);

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);

        var returnType = compiler.getJni().toNativeType(Type.getReturnType(this.target.getDesc()));

        var jniMethod = getJNIMethod(returnType, this.isStatic, this.isNonVirtual);

        // There is instance object for static methods
        var parameterValues = new LLVMValueRef[2 + this.params.length - (isStatic ? 0 : (isNonVirtual ? 0 : 1))];

        if (isStatic) {
            parameterValues[0] = classId;
            // methodID in params[1]
            parameterValues[1] = method;

            for (int i = 0; i < this.params.length; i++) {
                parameterValues[2 + i] = translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                                                                                                  this.params[i],
                                                                                                  this.targetTypes[i],
                                                                                                  true);
            }
        } else if (isNonVirtual) {
            parameterValues[0] = translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                                                                                          this.params[0],
                                                                                          this.targetTypes[0], false);
            parameterValues[1] = classId;
            // methodID in params[3]
            parameterValues[2] = method;

            for (int i = 0; i < this.params.length - 1; i++) {
                parameterValues[3 + i] = translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                                                                                                  this.params[i + 1],
                                                                                                  this.targetTypes[i + 1],
                                                                                                  true);
            }
        } else {
            parameterValues[0] = translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                                                                                          this.params[0],
                                                                                          this.targetTypes[0], false);
            // methodID in params[1]
            parameterValues[1] = method;

            for (int i = 0; i < this.params.length - 1; i++) {
                parameterValues[2 + i] = translatedMethod.getStack().buildStackTypeFixedStackLoad(translatedMethod.getLlvmBuilder(),
                                                                                                  this.params[i + 1],
                                                                                                  this.targetTypes[i + 1],
                                                                                                  true);
            }
        }

        var returnValue = compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                jniMethod,
                parameterValues
        );

        if (returnType != JNIType.VOID) {
            translatedMethod.getStack().buildStackStore(translatedMethod.getLlvmBuilder(),
                    this.resultTarget,
                    returnValue,
                    true);
        }

        // Did an exception occur?
        block.buildExceptionCheck(compiler, translatedMethod);
    }

    private JNIEnv.JNIEnvMethod getJNIMethod(JNIType jniType, boolean isStatic, boolean isNonVirtual) {
        if (isStatic) {
            switch (jniType) {
                case VOID:
                    return JNIEnv.JNIEnvMethod.CallStaticVoidMethod;
                case BOOLEAN:
                    return JNIEnv.JNIEnvMethod.CallStaticBooleanMethod;
                case CHAR:
                    return JNIEnv.JNIEnvMethod.CallStaticCharMethod;
                case BYTE:
                    return JNIEnv.JNIEnvMethod.CallStaticByteMethod;
                case SHORT:
                    return JNIEnv.JNIEnvMethod.CallStaticShortMethod;
                case INT:
                    return JNIEnv.JNIEnvMethod.CallStaticIntMethod;
                case LONG:
                    return JNIEnv.JNIEnvMethod.CallStaticLongMethod;
                case FLOAT:
                    return JNIEnv.JNIEnvMethod.CallStaticFloatMethod;
                case DOUBLE:
                    return JNIEnv.JNIEnvMethod.CallStaticDoubleMethod;
                case OBJECT:
                    return JNIEnv.JNIEnvMethod.CallStaticObjectMethod;
                default:
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        } else if (isNonVirtual) {
            switch (jniType) {
                case VOID:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualVoidMethod;
                case BOOLEAN:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualBooleanMethod;
                case CHAR:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualCharMethod;
                case BYTE:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualByteMethod;
                case SHORT:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualShortMethod;
                case INT:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualIntMethod;
                case LONG:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualLongMethod;
                case FLOAT:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualFloatMethod;
                case DOUBLE:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualDoubleMethod;
                case OBJECT:
                    return JNIEnv.JNIEnvMethod.CallNonvirtualObjectMethod;
                default:
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        } else {
            switch (jniType) {
                case VOID:
                    return JNIEnv.JNIEnvMethod.CallVoidMethod;
                case BOOLEAN:
                    return JNIEnv.JNIEnvMethod.CallBooleanMethod;
                case CHAR:
                    return JNIEnv.JNIEnvMethod.CallCharMethod;
                case BYTE:
                    return JNIEnv.JNIEnvMethod.CallByteMethod;
                case SHORT:
                    return JNIEnv.JNIEnvMethod.CallShortMethod;
                case INT:
                    return JNIEnv.JNIEnvMethod.CallIntMethod;
                case LONG:
                    return JNIEnv.JNIEnvMethod.CallLongMethod;
                case FLOAT:
                    return JNIEnv.JNIEnvMethod.CallFloatMethod;
                case DOUBLE:
                    return JNIEnv.JNIEnvMethod.CallDoubleMethod;
                case OBJECT:
                    return JNIEnv.JNIEnvMethod.CallObjectMethod;
                default:
                    throw new IllegalStateException("Unexpected value: " + jniType);
            }
        }
    }
}
