package net.superblaubeere27.masxinlingvaj.compiler.code.codegen;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNI;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.LLVMIntrinsic;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.HashMap;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStructGEP;

public class OnLoadBuilder {
    private final MLVCompiler compiler;
    private final HashMap<String, LLVMValueRef> classGlobals = new HashMap<>();
    private final HashMap<ClassMember, LLVMValueRef> memberGlobals = new HashMap<>();

    public OnLoadBuilder(MLVCompiler compiler) {
        this.compiler = compiler;
    }

    public LLVMValueRef buildFindClass(LLVMBuilderRef builder, String className) {
        return LLVM.LLVMBuildLoad(builder, this.getOrCreateClassGlobal(className), className + "_ref");
    }

    public LLVMValueRef buildFindMethod(LLVMBuilderRef builder, MethodOrFieldIdentifier identifier, boolean isStatic) {
        this.getOrCreateClassGlobal(identifier.getOwner());

        return LLVM.LLVMBuildLoad(builder, this.getOrCreateClassMemberGlobal(new ClassMember(identifier, ClassMemberType.METHOD, isStatic)), identifier + "_ref");
    }

    public LLVMValueRef buildFindField(LLVMBuilderRef builder, MethodOrFieldIdentifier identifier, boolean isStatic) {
        this.getOrCreateClassGlobal(identifier.getOwner());

        return LLVM.LLVMBuildLoad(builder, this.getOrCreateClassMemberGlobal(new ClassMember(identifier, ClassMemberType.FIELD, isStatic)), identifier + "_ref");
    }

    private LLVMValueRef getOrCreateClassGlobal(String name) {
        return this.classGlobals.computeIfAbsent(name, n -> {
            LLVMValueRef global = LLVM.LLVMAddGlobal(this.compiler.getModule(), JNIType.OBJECT.getLLVMType(), n);

            LLVM.LLVMSetUnnamedAddress(global, LLVM.LLVMLocalUnnamedAddr);
            LLVM.LLVMSetLinkage(global, LLVM.LLVMPrivateLinkage);
            LLVM.LLVMSetInitializer(global, LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType()));
            LLVM.LLVMSetAlignment(global, 8);

            return global;
        });
    }

    private LLVMValueRef getOrCreateClassMemberGlobal(ClassMember classMember) {
        return this.memberGlobals.computeIfAbsent(classMember, n -> {
            LLVMValueRef global = LLVM.LLVMAddGlobal(this.compiler.getModule(), JNIType.OBJECT.getLLVMType(), n.identifier.toString());

            LLVM.LLVMSetUnnamedAddress(global, LLVM.LLVMLocalUnnamedAddr);
            LLVM.LLVMSetLinkage(global, LLVM.LLVMPrivateLinkage);
            LLVM.LLVMSetInitializer(global, LLVM.LLVMConstNull(JNIType.OBJECT.getLLVMType()));
            LLVM.LLVMSetAlignment(global, 8);

            return global;
        });
    }

    private LLVMTypeRef getJavaVMType() {
        return LLVM.LLVMPointerType(LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0), 0);
    }

    public void compileMethods() {
        compileOnLoad();
    }

    private void compileOnLoad() {
        var function = LLVM.LLVMAddFunction(this.compiler.getModule(), "JNI_OnLoad", LLVM.LLVMFunctionType(JNIType.INT.getLLVMType(), new PointerPointer<>(LLVM.LLVMPointerType(this.getJavaVMType(), 0), LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0)), 2, 0));

        LLVM.LLVMSetDLLStorageClass(function, LLVM.LLVMDLLExportStorageClass);

        var builder = LLVM.LLVMCreateBuilder();

        LLVMValueRef envPtr;

        // GetEnv()
        {
            var getEnvBlock = LLVM.LLVMAppendBasicBlock(function, "get_env");

            LLVM.LLVMPositionBuilderAtEnd(builder, getEnvBlock);

            var envPtrPtr = LLVM.LLVMBuildAlloca(builder, LLVM.LLVMPointerType(this.compiler.getJni().getJniEnv().getType(), 0), "JNIEnv**");

            var bitCastedEnvPtrPtr = LLVM.LLVMBuildBitCast(builder, envPtrPtr, LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0), "");

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    this.compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_START,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedEnvPtrPtr
            );

            var fnTablePtr = LLVM.LLVMBuildLoad(builder, LLVM.LLVMGetParam(function, 0), "");

            var fnPtrPtr =
                    LLVM.LLVMBuildInBoundsGEP(builder,
                            fnTablePtr
                            , LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), 6, 1), 1, new BytePointer(""));

            var fnPtr = LLVM.LLVMBuildLoad(builder, fnPtrPtr, "");

            var fnType = LLVM.LLVMFunctionType(JNIType.INT.getLLVMType(), new PointerPointer<>(LLVM.LLVMPointerType(this.getJavaVMType(), 0), LLVM.LLVMPointerType(LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0), 0), JNIType.INT.getLLVMType()), 3, 0);

            var fnPtrCasted = LLVM.LLVMBuildBitCast(builder, fnPtr, LLVM.LLVMPointerType(fnType, 0), "fn_ptr");

            var fnResult = LLVM.LLVMBuildCall(builder, fnPtrCasted, new PointerPointer<>(LLVM.LLVMGetParam(function, 0), LLVM.LLVMBuildBitCast(builder, envPtrPtr, LLVM.LLVMPointerType(LLVM.LLVMPointerType(LLVM.LLVMInt8Type(), 0), 0), ""), LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), JNI.JNI_VERSION, 0)), 3, "GetEnv");

            envPtr = LLVM.LLVMBuildLoad(builder, envPtrPtr, "");

            // Call the llvm.lifetime.start intrinsic
            LLVMUtils.generateIntrinsicCall(
                    this.compiler,
                    builder,
                    LLVMIntrinsic.LIFETIME_END,
                    LLVM.LLVMConstInt(LLVM.LLVMInt64Type(), -1, 1),
                    bitCastedEnvPtrPtr
            );


            var errorBlock = LLVM.LLVMAppendBasicBlock(function, "get_env_err");
            var successBlock = LLVM.LLVMAppendBasicBlock(function, "success");

            LLVM.LLVMBuildCondBr(builder, LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntNE, fnResult, LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), 0, 0), ""), errorBlock, successBlock);

            LLVM.LLVMPositionBuilderAtEnd(builder, errorBlock);

            LLVM.LLVMBuildRet(builder, fnResult);

            LLVM.LLVMPositionBuilderAtEnd(builder, successBlock);

        }

        var functionTable = LLVMBuildLoad(builder,
                LLVMBuildStructGEP(builder, envPtr, 0, ""),
                "function_table");

        var errorBlock = LLVM.LLVMAppendBasicBlock(function, "error");

        this.classGlobals.forEach((className, global) -> {
            var findClassLocal = this.compiler.getJni().getJniEnv().callEnvironmentMethod(envPtr, JNIEnv.JNIEnvMethod.FindClass, builder, functionTable, LLVM.LLVMBuildGlobalStringPtr(builder, className, "className"));

            var nextBlock = LLVM.LLVMAppendBasicBlock(function, "");

            LLVM.LLVMBuildCondBr(builder, LLVM.LLVMBuildIsNull(builder, findClassLocal, ""), errorBlock, nextBlock);

            LLVM.LLVMPositionBuilderAtEnd(builder, nextBlock);

            var findClassGlobal = compiler.getJni().getJniEnv().callEnvironmentMethod(envPtr, JNIEnv.JNIEnvMethod.NewGlobalRef, builder, functionTable, findClassLocal);

            compiler.getJni().getJniEnv().callEnvironmentMethod(envPtr, JNIEnv.JNIEnvMethod.DeleteLocalRef, builder, functionTable, findClassLocal);

            LLVM.LLVMBuildStore(builder, findClassGlobal, global);
        });

        this.memberGlobals.forEach((member, global) -> {
            var memberId = this.compiler.getJni().getJniEnv().callEnvironmentMethod(envPtr, member.getLookupMethod(), builder, functionTable, this.buildFindClass(builder, member.identifier.getOwner()), LLVM.LLVMBuildGlobalStringPtr(builder, member.identifier.getName(), member.identifier.getName() + "_name"), LLVM.LLVMBuildGlobalStringPtr(builder, member.identifier.getDesc(), member.identifier.getName() + "_sig"));

            var nextBlock = LLVM.LLVMAppendBasicBlock(function, "");

            LLVM.LLVMBuildCondBr(builder, LLVM.LLVMBuildIsNull(builder, memberId, ""), errorBlock, nextBlock);

            LLVM.LLVMPositionBuilderAtEnd(builder, nextBlock);

            LLVM.LLVMBuildStore(builder, memberId, global);
        });

        LLVM.LLVMBuildRet(builder, LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), JNI.JNI_VERSION, 1));


        LLVM.LLVMPositionBuilderAtEnd(builder, errorBlock);

        LLVM.LLVMBuildRet(builder, LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), JNI.JNI_ERR, 1));
    }

    private enum ClassMemberType {
        METHOD, FIELD
    }

    private static class ClassMember {
        private final MethodOrFieldIdentifier identifier;
        private final ClassMemberType memberType;
        private final boolean isStatic;

        private ClassMember(MethodOrFieldIdentifier identifier, ClassMemberType memberType, boolean isStatic) {
            this.identifier = identifier;
            this.memberType = memberType;
            this.isStatic = isStatic;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassMember that = (ClassMember) o;
            return isStatic == that.isStatic && Objects.equals(identifier, that.identifier) && memberType == that.memberType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, memberType, isStatic);
        }

        private JNIEnv.JNIEnvMethod getLookupMethod() {
            if (this.memberType == ClassMemberType.METHOD) {
                return this.isStatic ? JNIEnv.JNIEnvMethod.GetStaticMethodID : JNIEnv.JNIEnvMethod.GetMethodID;
            } else if (this.memberType == ClassMemberType.FIELD) {
                return this.isStatic ? JNIEnv.JNIEnvMethod.GetStaticFieldID : JNIEnv.JNIEnvMethod.GetFieldID;
            }

            throw new IllegalStateException();
        }
    }
}
