package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Locale;

public class CmpToZeroBranchInstruction extends TerminatingInstruction {
    private final StackSlot input;
    private final Block ifTarget;
    private final Block elseTarget;
    private final ComparisionType comparisionType;

    public CmpToZeroBranchInstruction(StackSlot input, Block ifTarget, Block elseTarget, ComparisionType comparisionType) {
        if (comparisionType == ComparisionType.ACMP_EQ || comparisionType == ComparisionType.ACMP_NE)
            throw new IllegalArgumentException("Cannot compare an argument with zero");

        this.input = input;
        this.ifTarget = ifTarget;
        this.elseTarget = elseTarget;
        this.comparisionType = comparisionType;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var stack = translatedMethod.getStack();

        var operand = stack.buildStackLoad(builder, this.input);

        LLVMValueRef comparisonResult;

        switch (this.comparisionType) {
            case ICMP_EQ:
            case ICMP_NE:
            case ICMP_LT:
            case ICMP_GE:
            case ICMP_GT:
            case ICMP_LE:
                comparisonResult = LLVM.LLVMBuildICmp(builder,
                        this.comparisionType.getLLVMPredicate(),
                        operand,
                        LLVM.LLVMConstInt(LLVM.LLVMInt32Type(), 0, 1),
                        this.comparisionType.toString().toLowerCase(Locale.ROOT));
                break;
            case IF_NULL:
                comparisonResult = LLVM.LLVMBuildIsNull(builder,
                        operand,
                        this.comparisionType.toString().toLowerCase(Locale.ROOT));
                break;
            case IF_NOT_NULL:
                comparisonResult = LLVM.LLVMBuildIsNotNull(builder,
                        operand,
                        this.comparisionType.toString().toLowerCase(Locale.ROOT));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.comparisionType);
        }

        LLVM.LLVMBuildCondBr(builder, comparisonResult, this.ifTarget.getLlvmBlock(), this.elseTarget.getLlvmBlock());
    }
}
