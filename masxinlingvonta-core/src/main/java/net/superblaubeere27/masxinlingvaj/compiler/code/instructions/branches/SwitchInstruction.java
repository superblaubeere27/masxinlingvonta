package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;

public class SwitchInstruction extends TerminatingInstruction {
    private final StackSlot operand;
    private final Block defaultTarget;
    private final SwitchCase[] cases;

    public SwitchInstruction(StackSlot operand, Block defaultTarget, SwitchCase[] cases) {
        this.operand = operand;
        this.defaultTarget = defaultTarget;
        this.cases = cases;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builtSwitch = LLVM.LLVMBuildSwitch(
                translatedMethod.getLlvmBuilder(),
                translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.operand),
                this.defaultTarget.getLlvmBlock(),
                this.cases.length
        );

        for (SwitchCase aCase : this.cases) {
            LLVM.LLVMAddCase(builtSwitch,
                    LLVM.LLVMConstInt(JNIType.INT.getLLVMType(), aCase.key, 1),
                    aCase.target.getLlvmBlock());
        }
    }

    public static class SwitchCase {
        private final int key;
        private final Block target;

        public SwitchCase(int key, Block target) {
            this.key = key;
            this.target = target;
        }
    }
}
