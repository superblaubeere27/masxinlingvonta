package net.superblaubeere27.masxinlingvaj.compiler.stack;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.utils.OpcodeUtils;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;

public class MethodStack {
    private final CompilerMethod method;
    private final LLVMBasicBlockRef allocationsBlock;
    private final HashMap<StackSlot, StackEntry> locals = new HashMap<>();
    private final HashMap<StackSlot, StackEntry> stack = new HashMap<>();

    public MethodStack(CompilerMethod method, LLVMBasicBlockRef allocationsBlock) {
        this.method = method;
        this.allocationsBlock = allocationsBlock;
    }

    /**
     * Allocates local variables for every used stack slot in the given method
     */
    public final void initStackVariables(MLVCompiler compiler, LLVMBuilderRef builder) {
        // Set the position to the basic block where the allocations are supposed to take place.
        LLVM.LLVMPositionBuilderAtEnd(builder, this.allocationsBlock);

        // Register method parameters
        int paramIndex = 0;

        if (!this.method.isStatic()) {
            ensureLocalAllocated(new StackSlot(JNIType.OBJECT, paramIndex++), builder);
        }

        for (Type argumentType : Type.getArgumentTypes(this.method.getNode().desc)) {
            ensureLocalAllocated(new StackSlot(compiler.getJni().toNativeType(argumentType).getStackStorageType(),
                                               paramIndex), builder);

            paramIndex += argumentType.getSize();
        }

        for (AbstractInsnNode instruction : this.method.getNode().instructions) {
            if (instruction instanceof VarInsnNode) {
                this.ensureLocalAllocated(new StackSlot(compiler.getJni().toNativeType(OpcodeUtils.getReturnType(
                        instruction)), ((VarInsnNode) instruction).var), builder);
            }
        }
    }

    /**
     * Checks if a local slot is already allocated, otherwise allocate it using the builder
     */
    private void ensureLocalAllocated(StackSlot stackSlot, LLVMBuilderRef builder) {
        this.locals.computeIfAbsent(stackSlot,
                slot -> new StackEntry(LLVM.LLVMBuildAlloca(builder,
                        slot.getType().getLLVMType(),
                        "locals[" + slot.getIndex() + "]: " + slot.getType())));
    }

    private StackEntry getStack(StackSlot stackSlot, LLVMBasicBlockRef currentBlock, LLVMBuilderRef builder) {
        return this.stack.computeIfAbsent(stackSlot, slot -> {
            LLVM.LLVMPositionBuilderAtEnd(builder, this.allocationsBlock);

            var entry = new StackEntry(LLVM.LLVMBuildAlloca(builder,
                    slot.getType().getLLVMType(),
                    "stack[" + slot.getIndex() + "]: " + slot.getType()));

            LLVM.LLVMPositionBuilderAtEnd(builder, currentBlock);

            return entry;
        });
    }

    public void buildStackStore(LLVMBuilderRef builder, StackSlot stackSlot, LLVMValueRef value) {
        buildStackStore(builder, stackSlot, value, false);
    }

    public void buildStackStore(LLVMBuilderRef builder, StackSlot stackSlot, LLVMValueRef value, boolean fixType) {
        var currentBB = LLVM.LLVMGetInsertBlock(builder);

        if (fixType) {
            value = fixType(builder, stackSlot, value);
        }

        LLVM.LLVMBuildStore(builder, value, getStack(stackSlot, currentBB, builder).getAllocatedValue());
    }

    public LLVMValueRef buildStackLoad(LLVMBuilderRef builder, StackSlot stackSlot) {
        var currentBB = LLVM.LLVMGetInsertBlock(builder);

        return LLVM.LLVMBuildLoad(builder, getStack(stackSlot, currentBB, builder).getAllocatedValue(), "");
    }

    public LLVMValueRef buildStackTypeFixedStackLoad(LLVMBuilderRef builder, StackSlot stackSlot, JNIType type, boolean vararg) {
        var currentBB = LLVM.LLVMGetInsertBlock(builder);

        var value = LLVM.LLVMBuildLoad(builder, getStack(stackSlot, currentBB, builder).getAllocatedValue(), "");

        if (vararg && type == JNIType.FLOAT) {
            if (LLVM.LLVMGetTypeKind(LLVM.LLVMTypeOf(value)) == LLVM.LLVMFloatTypeKind)
                value = LLVM.LLVMBuildFPExt(builder, value, LLVM.LLVMDoubleType(), "vararg_fix");
        } else {
            value = LLVM.LLVMBuildTrunc(builder, value, type.getLLVMType(), "");
        }


        return value;
    }

    public void buildLocalStore(LLVMBuilderRef builder, StackSlot stackSlot, LLVMValueRef value, boolean fixTypes) {
        if (fixTypes)
            value = fixType(builder, stackSlot, value);

        LLVM.LLVMBuildStore(builder, value, this.locals.get(stackSlot).getAllocatedValue());
    }

    public void buildLocalStore(LLVMBuilderRef builder, StackSlot stackSlot, LLVMValueRef value) {
        this.buildLocalStore(builder, stackSlot, value, true);
    }

    public LLVMValueRef buildLocalLoad(LLVMBuilderRef builder, StackSlot stackSlot) {
        return LLVM.LLVMBuildLoad(builder, this.locals.get(stackSlot).getAllocatedValue(), "");
    }

    private LLVMValueRef fixType(LLVMBuilderRef builder, StackSlot stackSlot, LLVMValueRef value) {
        return LLVM.LLVMBuildZExt(builder, value, stackSlot.getType().getLLVMType(), "");
    }

    public LLVMBasicBlockRef getAllocationsBlock() {
        return allocationsBlock;
    }
}
