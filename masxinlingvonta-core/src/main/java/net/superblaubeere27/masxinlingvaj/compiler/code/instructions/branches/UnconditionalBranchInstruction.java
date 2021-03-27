package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import org.bytedeco.llvm.global.LLVM;

public class UnconditionalBranchInstruction extends TerminatingInstruction {
    private final Block target;

    public UnconditionalBranchInstruction(Block target) {
        this.target = target;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        LLVM.LLVMBuildBr(translatedMethod.getLlvmBuilder(), this.target.getLlvmBlock());
    }
}
