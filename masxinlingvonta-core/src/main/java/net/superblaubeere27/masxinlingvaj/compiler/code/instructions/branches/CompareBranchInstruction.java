package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Locale;

public class CompareBranchInstruction extends TerminatingInstruction {
    private final StackSlot lhs;
    private final StackSlot rhs;
    private final Block ifTarget;
    private final Block elseTarget;
    private final ComparisionType comparisionType;

    public CompareBranchInstruction(StackSlot lhs, StackSlot rhs, Block ifTarget, Block elseTarget, ComparisionType comparisionType) {
        if (comparisionType == ComparisionType.IF_NULL || comparisionType == ComparisionType.IF_NOT_NULL)
            throw new IllegalArgumentException("Cannot compare an argument with zero");

        this.lhs = lhs;
        this.rhs = rhs;
        this.ifTarget = ifTarget;
        this.comparisionType = comparisionType;
        this.elseTarget = elseTarget;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var stack = translatedMethod.getStack();

        var lhsOperand = stack.buildStackLoad(builder, this.lhs);
        var rhsOperand = stack.buildStackLoad(builder, this.rhs);

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
                        lhsOperand,
                        rhsOperand,
                        this.comparisionType.toString().toLowerCase(Locale.ROOT));
                break;
            case ACMP_EQ:
            case ACMP_NE:
                comparisonResult = LLVM.LLVMBuildICmp(builder,
                        this.comparisionType.getLLVMPredicate(),
                        compiler.getJni().getJniEnv().callEnvironmentMethod(
                                translatedMethod, translatedMethod.getEnvPtr(),
                                JNIEnv.JNIEnvMethod.IsSameObject,
                                lhsOperand,
                                rhsOperand
                        ),
                        LLVM.LLVMConstInt(JNIType.BOOLEAN.getLLVMType(), 0, 1),
                        this.comparisionType.toString().toLowerCase(Locale.ROOT));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.comparisionType);
        }

        LLVM.LLVMBuildCondBr(builder, comparisonResult, this.ifTarget.getLlvmBlock(), this.elseTarget.getLlvmBlock());
    }
}
