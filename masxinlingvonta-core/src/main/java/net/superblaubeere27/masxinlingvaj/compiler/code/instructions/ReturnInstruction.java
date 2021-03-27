package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches.TerminatingInstruction;
import org.bytedeco.llvm.global.LLVM;

public class ReturnInstruction extends TerminatingInstruction {

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        // Not much to do here...
        LLVM.LLVMBuildRetVoid(translatedMethod.getLlvmBuilder());
    }

}
