package net.superblaubeere27.masxinlingvaj.compiler;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNI;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.MethodStack;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.Mangle;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

import static net.superblaubeere27.masxinlingvaj.utils.TypeUtils.getEffectiveArgumentTypes;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStructGEP;

public class TranslatedMethod {
    private final MethodStack stack;
    private final LLVMValueRef llvmFunction;
    private final LLVMBuilderRef llvmBuilder;
    private final CompilerMethod compilerMethod;

    /**
     * Cached &env.table
     */
    private final LLVMValueRef jniFunctionTable;

    private TranslatedMethod(LLVMValueRef llvmFunction, MethodStack stack, LLVMBuilderRef llvmBuilder, CompilerMethod compilerMethod, LLVMValueRef jniFunctionTable) {
        this.llvmFunction = llvmFunction;
        this.stack = stack;
        this.llvmBuilder = llvmBuilder;
        this.compilerMethod = compilerMethod;
        this.jniFunctionTable = jniFunctionTable;
    }

    static TranslatedMethod createFromCompilerMethod(MLVCompiler compiler, CompilerMethod compilerMethod) {
        var mangledName = Mangle.mangleMethod(compilerMethod.getIdentifier());
        var jni = compiler.getJni();

        // Get the JVM argument types and return type of the function
        var argumentTypes = getEffectiveArgumentTypes(compilerMethod);
        var returnType = Type.getReturnType(compilerMethod.getNode().desc);

        // The index the params given to the method start
        int paramIdx = compilerMethod.isStatic() ? 2 : 1;

        // static methods don't have the *this* parameter
        LLVMTypeRef[] paramTypes = new LLVMTypeRef[paramIdx + argumentTypes.length];

        // param_0 = JNIEnv*
        paramTypes[0] = LLVM.LLVMPointerType(jni.getJniEnv().getType(), 0);
        // param_1 = jclass, overwritten by the instance argument if the method is not static
        paramTypes[1] = JNIType.OBJECT.getLLVMType();

        for (int i = 0; i < argumentTypes.length; i++) {
            paramTypes[paramIdx + i] = jni.toNativeType(argumentTypes[i]).getLLVMType();
        }

        var method = LLVM.LLVMAddFunction(compiler.getModule(),
                mangledName,
                LLVM.LLVMFunctionType(jni.toNativeType(returnType).getLLVMType(),
                        new PointerPointer<>(paramTypes),
                        paramTypes.length,
                        0));

        LLVM.LLVMSetDLLStorageClass(method, LLVM.LLVMDLLExportStorageClass);

        var stack = new MethodStack(compilerMethod, LLVM.LLVMAppendBasicBlock(method, "stack-allocs"));

        var builder = LLVM.LLVMCreateBuilder();

        stack.initStackVariables(compiler, builder);

        var paramStackIdx = 0;

        for (int i = 0; i < argumentTypes.length; i++) {
            stack.buildLocalStore(builder,
                                  new StackSlot(jni.toNativeType(argumentTypes[i]).getStackStorageType(),
                                                paramStackIdx),
                                  LLVM.LLVMGetParam(method, paramIdx + i),
                                  true);

            paramStackIdx += argumentTypes[i].getSize();
        }

        var functionTable = LLVMBuildLoad(builder,
                LLVMBuildStructGEP(builder, LLVM.LLVMGetParam(method, 0), 0, ""),
                "function_table");

        return new TranslatedMethod(method, stack, builder, compilerMethod, functionTable);
    }

    public LLVMBuilderRef getLlvmBuilder() {
        return llvmBuilder;
    }

    public LLVMValueRef getLlvmFunction() {
        return llvmFunction;
    }

    public MethodStack getStack() {
        return stack;
    }

    public CompilerMethod getCompilerMethod() {
        return compilerMethod;
    }

    public LLVMValueRef getEnvPtr() {
        return LLVM.LLVMGetParam(this.getLlvmFunction(), 0);
    }

    public LLVMValueRef buildFindClass(JNI jni, String clazz) {
        return jni.getJniEnv().callEnvironmentMethod(this,
                this.getEnvPtr(),
                JNIEnv.JNIEnvMethod.FindClass,
                LLVM.LLVMBuildGlobalStringPtr(this.llvmBuilder, clazz, "class"));
    }

    public LLVMValueRef buildGetFieldID(JNI jni, MethodOrFieldIdentifier identifier, LLVMValueRef classId, boolean isStatic) {
        return jni.getJniEnv().callEnvironmentMethod(
                this, this.getEnvPtr(),
                isStatic ? JNIEnv.JNIEnvMethod.GetStaticFieldID : JNIEnv.JNIEnvMethod.GetFieldID,
                classId,
                LLVM.LLVMBuildGlobalStringPtr(this.llvmBuilder, identifier.getName(), "field_name"),
                LLVM.LLVMBuildGlobalStringPtr(this.llvmBuilder, identifier.getDesc(), "field_sig")
        );
    }

    public LLVMValueRef buildGetMethodID(JNI jni, MethodOrFieldIdentifier identifier, LLVMValueRef classId, boolean isStatic) {
        return jni.getJniEnv().callEnvironmentMethod(
                this, this.getEnvPtr(),
                isStatic ? JNIEnv.JNIEnvMethod.GetStaticMethodID : JNIEnv.JNIEnvMethod.GetMethodID,
                classId,
                LLVM.LLVMBuildGlobalStringPtr(this.llvmBuilder, identifier.getName(), "method_name"),
                LLVM.LLVMBuildGlobalStringPtr(this.llvmBuilder, identifier.getDesc(), "method_sig")
        );
    }

    public LLVMValueRef getJniFunctionTable() {
        return jniFunctionTable;
    }

    @Override
    protected void finalize() throws Throwable {
        LLVM.LLVMDisposeBuilder(this.llvmBuilder);
    }
}
