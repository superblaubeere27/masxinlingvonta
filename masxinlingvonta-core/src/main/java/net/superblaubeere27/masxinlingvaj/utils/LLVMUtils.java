package net.superblaubeere27.masxinlingvaj.utils;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

public class LLVMUtils {

    public static LLVMValueRef generateIntrinsicCall(MLVCompiler compiler, LLVMBuilderRef builder, LLVMIntrinsic intrinsic, LLVMValueRef... values) {
        var decl = LLVM.LLVMGetIntrinsicDeclaration(compiler.getModule(),
                intrinsic.getId(),
                new PointerPointer<>(intrinsic.getTypes()),
                intrinsic.getTypes().length);

        return LLVM.LLVMBuildCall(builder, decl, new PointerPointer<>(values), values.length, "");
    }

    /**
     * Creates an if-block in LLVM
     *
     * @param condition if this condition is met, the execution will jump to the built block
     * @param callback  builds the inside of the if-block. If this callback doesn't terminate the basic block, it is terminated with a jump to the next block
     */
    public static void buildIf(TranslatedMethod translatedMethod, LLVMValueRef condition, Runnable callback) {
        var builder = translatedMethod.getLlvmBuilder();

        var lastBlock = LLVM.LLVMGetInsertBlock(builder);

        var ifBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "if");
        var elseBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "else");

        // Rearrange the blocks...
        LLVM.LLVMMoveBasicBlockAfter(ifBlock, lastBlock);
        LLVM.LLVMMoveBasicBlockAfter(elseBlock, ifBlock);

        LLVM.LLVMBuildCondBr(builder, condition, ifBlock, elseBlock);

        // Build the contents of the if-block
        LLVM.LLVMPositionBuilderAtEnd(builder, ifBlock);

        callback.run();

        // If the callback doesn't produce a terminator, jump to the next block
        if (LLVM.LLVMGetBasicBlockTerminator(ifBlock) == null)
            LLVM.LLVMBuildBr(builder, elseBlock);

        LLVM.LLVMPositionBuilderAtEnd(builder, elseBlock);
    }

    /**
     * Creates an if-else-block in LLVM
     *
     * @param condition if this condition is met, the execution will jump to the if-block
     */
    public static void buildIfElse(TranslatedMethod translatedMethod, LLVMValueRef condition, Runnable ifBuilder, Runnable elseBuilder) {
        var builder = translatedMethod.getLlvmBuilder();

        var lastBlock = LLVM.LLVMGetInsertBlock(builder);

        var ifBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "if");
        var elseBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "else");
        var finBlock = LLVM.LLVMAppendBasicBlock(translatedMethod.getLlvmFunction(), "fin");

        // Rearrange the blocks...
        LLVM.LLVMMoveBasicBlockAfter(ifBlock, lastBlock);
        LLVM.LLVMMoveBasicBlockAfter(elseBlock, ifBlock);
        LLVM.LLVMMoveBasicBlockAfter(finBlock, elseBlock);

        LLVM.LLVMBuildCondBr(builder, condition, ifBlock, elseBlock);

        // Build the contents of the if-block
        LLVM.LLVMPositionBuilderAtEnd(builder, ifBlock);

        ifBuilder.run();

        LLVM.LLVMBuildBr(builder, finBlock);

        // Build the contents of the else-block
        LLVM.LLVMPositionBuilderAtEnd(builder, elseBlock);

        elseBuilder.run();

        LLVM.LLVMBuildBr(builder, finBlock);

        LLVM.LLVMPositionBuilderAtEnd(builder, finBlock);
    }

}
