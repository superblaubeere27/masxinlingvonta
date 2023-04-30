package net.superblaubeere27.masxinlingvaj.compiler.code;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.LinkedList;

import static net.superblaubeere27.masxinlingvaj.utils.LLVMUtils.buildIf;

public class Block {
    private final LinkedList<ExceptionHandler> exceptionHandlers = new LinkedList<>();
    private LLVMBasicBlockRef llvmBlock;
    private Instruction[] instructions;
    private LLVMBasicBlockRef exceptionHandlerBlock;

    public void setInstructions(Instruction[] instructions) {
        if (!instructions[instructions.length - 1].isTerminating())
            throw new IllegalArgumentException("Basic block is not terminated");

        this.instructions = instructions;
    }

    public void addToMethod(TranslatedMethod translatedMethod) {
        this.llvmBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "");
    }

    public void addExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandlers.add(exceptionHandler);
    }

    /**
     * Compiles this basic block to the given method
     */
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod) {
        var builder = translatedMethod.getLlvmBuilder();

        LLVM.LLVMPositionBuilderAtEnd(builder, this.llvmBlock);

        for (Instruction instruction : this.instructions) {
            instruction.compile(compiler, translatedMethod, this);
        }

        if (LLVM.LLVMGetBasicBlockTerminator(this.llvmBlock) == null) {
            throw new IllegalStateException("Compiled basic block is not terminated");
        }
    }

    /**
     * Creates code that checks if an exception was thrown and in case handles it.
     */
    public void buildExceptionCheck(MLVCompiler compiler, TranslatedMethod translatedMethod) {
        var builder = translatedMethod.getLlvmBuilder();

        var exceptionThrown = compiler.getJni().getJniEnv().callEnvironmentMethod(
                translatedMethod, translatedMethod.getEnvPtr(),
                JNIEnv.JNIEnvMethod.ExceptionCheck
        );

        var okBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "no_exception");
        var exceptionBlock = getExceptionBlock(compiler, translatedMethod);

        LLVM.LLVMMoveBasicBlockAfter(okBlock, LLVM.LLVMGetInsertBlock(translatedMethod.getLlvmBuilder()));

        LLVM.LLVMBuildCondBr(
                builder,
                LLVM.LLVMBuildICmp(builder,
                        LLVM.LLVMIntEQ,
                        exceptionThrown,
                        LLVM.LLVMConstInt(LLVM.LLVMTypeOf(exceptionThrown), 0, 0),
                        ""),
                okBlock,
                exceptionBlock
        );

        LLVM.LLVMPositionBuilderAtEnd(builder, okBlock);
    }

    /**
     * Gets or builds the exception handler block
     *
     * @return a basic block reference to the exception handler block
     */
    public LLVMBasicBlockRef getExceptionBlock(MLVCompiler compiler, TranslatedMethod translatedMethod) {
        if (this.exceptionHandlerBlock != null) {
            return this.exceptionHandlerBlock;
        }

        var builder = translatedMethod.getLlvmBuilder();

        // The block the builder was appending until this method was called
        var lastBlock = LLVM.LLVMGetInsertBlock(builder);
        var exceptionHandlerBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "exception_handler");

        LLVM.LLVMMoveBasicBlockAfter(exceptionHandlerBlock, lastBlock);

        // Set the builder's position to the exception handler block
        LLVM.LLVMPositionBuilderAtEnd(builder, exceptionHandlerBlock);

        var currentBlock = exceptionHandlerBlock;

        ExceptionBuilder:
        {
            var exception = compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    JNIEnv.JNIEnvMethod.ExceptionOccurred
            );

            // Store the exception in stack slot 0
            translatedMethod.getStack().buildStackStore(
                    builder,
                    new StackSlot(JNIType.OBJECT, 0),
                    exception
            );

            for (ExceptionHandler exceptionHandler : this.exceptionHandlers) {
                if (exceptionHandler.getType() == null) {
                    // Jump to the handler
                    LLVM.LLVMBuildBr(builder, exceptionHandler.getHandlerBlock().getLlvmBlock());

                    // The exception was definitely handled, no need to stop the method execution
                    break ExceptionBuilder;
                } else {
                    // Does the thrown exception's type match the handler's type?
                    var isInstanceOf = compiler.getJni().getJniEnv().callEnvironmentMethod(
                            translatedMethod, translatedMethod.getEnvPtr(),
                            JNIEnv.JNIEnvMethod.IsInstanceOf,
                            exception,
                            translatedMethod.buildFindClass(compiler.getJni(), exceptionHandler.getType())
                    );

                    var ifBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "");

                    LLVM.LLVMMoveBasicBlockAfter(ifBlock, currentBlock);

                    var elseBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "");

                    LLVM.LLVMMoveBasicBlockAfter(elseBlock, currentBlock);

                    LLVM.LLVMBuildCondBr(
                            builder,
                            LLVM.LLVMBuildICmp(builder,
                                    LLVM.LLVMIntEQ,
                                    isInstanceOf,
                                    LLVM.LLVMConstInt(LLVM.LLVMTypeOf(isInstanceOf), 0, 0),
                                    ""),
                            elseBlock,
                            ifBlock
                    );

                    LLVM.LLVMPositionBuilderAtEnd(builder, ifBlock);

                    // The exception was caught, clear it
                    compiler.getJni().getJniEnv().callEnvironmentMethod(
                            translatedMethod, translatedMethod.getEnvPtr(),
                            JNIEnv.JNIEnvMethod.ExceptionClear
                    );

                    LLVM.LLVMBuildBr(builder, exceptionHandler.getHandlerBlock().getLlvmBlock());

                    LLVM.LLVMPositionBuilderAtEnd(builder, elseBlock);

                    currentBlock = elseBlock;
                }
            }

            // The exception wasn't caught? Then stop the method execution

            var retType = LLVM.LLVMGetReturnType(LLVM.LLVMGetElementType(LLVM.LLVMTypeOf(translatedMethod.getLlvmFunction())));
            var kind = LLVM.LLVMGetTypeKind(retType);

            if (kind == LLVM.LLVMVoidTypeKind) {
                LLVM.LLVMBuildRetVoid(builder);
            } else {
                LLVM.LLVMBuildRet(builder, LLVM.LLVMConstNull(retType));
            }
        }

        // Reset the builder position
        LLVM.LLVMPositionBuilderAtEnd(builder, lastBlock);

        this.exceptionHandlerBlock = exceptionHandlerBlock;

        return exceptionHandlerBlock;
    }

    public LLVMBasicBlockRef getLlvmBlock() {
        return llvmBlock;
    }

    public void throwIf(MLVCompiler compiler, TranslatedMethod translatedMethod, LLVMValueRef cond, String exceptionClass, String message) {
        buildIf(translatedMethod, cond, () -> {
            var builder = translatedMethod.getLlvmBuilder();

            compiler.getJni().getJniEnv().callEnvironmentMethod(
                    translatedMethod, translatedMethod.getEnvPtr(),
                    JNIEnv.JNIEnvMethod.ThrowNew,
                    translatedMethod.buildFindClass(compiler.getJni(), exceptionClass),
                    LLVM.LLVMBuildGlobalStringPtr(builder, message, "exception_msg")
            );

            LLVM.LLVMBuildBr(builder, getExceptionBlock(compiler, translatedMethod));
        });
    }
}
