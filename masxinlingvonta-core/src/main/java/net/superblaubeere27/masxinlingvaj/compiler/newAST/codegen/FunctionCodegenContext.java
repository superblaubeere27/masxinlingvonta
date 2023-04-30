package net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.Mangle;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

import static net.superblaubeere27.masxinlingvaj.utils.TypeUtils.getEffectiveArgumentTypes;
import static org.bytedeco.llvm.global.LLVM.*;

public class FunctionCodegenContext {
    private final HashMap<BasicBlock, LLVMBasicBlockRef> blockMap;
    private final ControlFlowGraph cfg;
    private final MLVCompiler compiler;
    private final LLVMValueRef function;
    private final LLVMBuilderRef builder;
    private final LLVMValueRef functionTable;
    private final HashMap<JNIType, LLVMValueRef> typeAllocas = new HashMap<>();
    private final HashMap<Local, LLVMValueRef> locals = new HashMap<>();

    public FunctionCodegenContext(MLVCompiler compiler, ControlFlowGraph cfg, LLVMValueRef function, LLVMBuilderRef builder, LLVMValueRef functionTable, HashMap<BasicBlock, LLVMBasicBlockRef> blockMap) {
        this.cfg = cfg;
        this.compiler = compiler;
        this.function = function;
        this.builder = builder;
        this.functionTable = functionTable;
        this.blockMap = blockMap;
    }

    public static FunctionCodegenContext createFunction(MLVCompiler compiler, ControlFlowGraph cfg) {
        var compilerMethod = cfg.getCompilerMethod();

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

        var function = LLVM.LLVMAddFunction(compiler.getModule(),
                mangledName,
                LLVM.LLVMFunctionType(jni.toNativeType(returnType).getLLVMType(),
                        new PointerPointer<>(paramTypes),
                        paramTypes.length,
                        0));

        LLVM.LLVMAddAttributeAtIndex(function, 1, LLVM.LLVMCreateEnumAttribute(LLVM.LLVMGetGlobalContext(),
                LLVM.LLVMGetEnumAttributeKindForName("readonly",
                        "readonly".length()),
                0));


//        if (!compilerMethod.isLibrary())
        LLVM.LLVMSetDLLStorageClass(function, LLVM.LLVMDLLExportStorageClass);
//        else
//            LLVM.LLVMSetLinkage(function, LLVM.LLVMPrivateLinkage);

        var builder = LLVM.LLVMCreateBuilder();

        LLVMBasicBlockRef preheader = LLVMAppendBasicBlock(function, "preheader");

        LLVM.LLVMPositionBuilderAtEnd(builder, preheader);

        var functionTable = LLVMBuildLoad(builder,
                LLVMBuildStructGEP(builder, LLVM.LLVMGetParam(function, 0), 0, ""),
                "function_table");

        HashMap<BasicBlock, LLVMBasicBlockRef> blockMap = new HashMap<>();

        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, "entry");

        blockMap.put(cfg.getEntry(), entry);

        for (BasicBlock vertex : cfg.verticesInOrder()) {
            blockMap.computeIfAbsent(vertex, x -> LLVM.LLVMAppendBasicBlock(function, x.toString()));
        }

        return new FunctionCodegenContext(compiler, cfg, function, builder, functionTable, blockMap);
    }

    public void compile() {
        var irCompiler = new ImmToLLVMIRCompiler(this);

        for (BasicBlock vertex : this.cfg.verticesInOrder()) {
            LLVMPositionBuilderAtEnd(this.builder, getLLVMBlock(vertex));

            for (Stmt stmt : vertex) {
                stmt.compile(irCompiler);
            }
        }

        appendToEntryBlock(() -> LLVMBuildBr(this.builder, this.getLLVMBlock(this.cfg.getEntry())));
    }

    public LLVMBasicBlockRef getLLVMBlock(BasicBlock vertex) {
        return Objects.requireNonNull(this.blockMap.get(vertex));
    }

    public LLVMBuilderRef getBuilder() {
        return builder;
    }

    public LLVMValueRef callEnvironmentMethod(JNIEnv.JNIEnvMethod method, LLVMValueRef... params) {
        return this.getCompiler().getJni().getJniEnv().callEnvironmentMethod(this.getEnvPtr(), method, this.builder, this.functionTable, params);
    }

    public LLVMValueRef buildGlobalString(String value, String name) {
        return LLVM.LLVMBuildGlobalStringPtr(this.builder, value, name);
    }

    private <T> T appendToEntryBlock(Supplier<T> r) {
        var builder = this.getBuilder();

        var currBlock = LLVM.LLVMGetInsertBlock(builder);

        LLVM.LLVMPositionBuilderAtEnd(builder, LLVM.LLVMGetEntryBasicBlock(this.function));

        var ret = r.get();

        LLVM.LLVMPositionBuilderAtEnd(builder, currBlock);

        return ret;
    }

    public LLVMValueRef buildFindClass(String clazz) {
        return this.getCompiler().getOnLoadBuilder().buildFindClass(this.builder, clazz);
    }

    public LLVMValueRef buildGetFieldID(MethodOrFieldIdentifier identifier, LLVMValueRef classId, boolean isStatic) {
        return this.getCompiler().getOnLoadBuilder().buildFindField(this.builder, identifier, isStatic);
    }

    public LLVMValueRef buildGetMethodID(MethodOrFieldIdentifier identifier, LLVMValueRef classId, boolean isStatic) {
        return this.getCompiler().getOnLoadBuilder().buildFindMethod(this.builder, identifier, isStatic);
    }

    /**
     * Fixes types that are JNI-Inbound (method stack -> JVM-Function)
     */
    public LLVMValueRef fixTypeIn(LLVMValueRef value, JNIType type, boolean vararg) {
        if (vararg && type == JNIType.FLOAT) {
            if (LLVM.LLVMGetTypeKind(LLVM.LLVMTypeOf(value)) == LLVM.LLVMFloatTypeKind)
                value = LLVM.LLVMBuildFPExt(this.builder, value, LLVM.LLVMDoubleType(), "vararg_fix");
        } else {
            value = LLVM.LLVMBuildTrunc(this.builder, value, type.getLLVMType(), "");
        }

        return value;
    }

    /**
     * Fixes types that are JNI-Outbound (JVM-Function -> method stack)
     */
    public LLVMValueRef fixTypeOut(LLVMValueRef value, JNIType from, JNIType toType) {
        return LLVM.LLVMBuildIntCast2(builder, value, toType.getLLVMType(), from.isSigned() ? 1 : 0, "");
    }

    public ImmType getReturnType() {
        return this.cfg.getReturnType();
    }

    public JNIType toNativeType(Type type) {
        return this.getCompiler().getJni().toNativeType(type);
    }

    public LLVMValueRef getEnvPtr() {
        return LLVM.LLVMGetParam(this.getLLVMFunction(), 0);
    }

    public LLVMValueRef getLLVMFunction() {
        return function;
    }

    public MLVCompiler getCompiler() {
        return compiler;
    }

    public void putLocal(Local local, LLVMValueRef expr) {
        var prevValue = this.locals.put(local, expr);

        if (prevValue != null) {
            LLVM.LLVMReplaceAllUsesWith(prevValue, expr);
            LLVM.LLVMInstructionEraseFromParent(prevValue);
        }
    }

    public LLVMValueRef getAllocaFor(JNIType type) {
        return this.typeAllocas.computeIfAbsent(type, t -> appendToEntryBlock(() -> LLVM.LLVMBuildAlloca(this.builder, t.getLLVMType(), "stack_" + t)));
    }

    public LLVMValueRef getLocal(Local local) {
        return this.locals.computeIfAbsent(local, l -> LLVM.LLVMBuildLoad(this.getBuilder(), LLVM.LLVMConstNull(LLVM.LLVMPointerType(l.getType().toNativeType().getLLVMType(), 0)), "placeholder"));
    }

    public ControlFlowGraph getCfg() {
        return cfg;
    }
}
