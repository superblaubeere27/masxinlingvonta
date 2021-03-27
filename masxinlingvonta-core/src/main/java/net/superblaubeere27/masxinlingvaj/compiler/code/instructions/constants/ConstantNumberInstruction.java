package net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

public class ConstantNumberInstruction extends ConstantInstruction {
    private final Number number;

    public ConstantNumberInstruction(StackSlot outputSlot, Number number) {
        super(outputSlot);

        this.number = number;
    }


    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var type = super.outputSlot.getType().getLLVMType();

        LLVMValueRef value;

        switch (super.outputSlot.getType()) {
            case INT:
                value = LLVM.LLVMConstInt(type, this.number.intValue(), 1);
                break;
            case LONG:
                value = LLVM.LLVMConstInt(type, this.number.longValue(), 1);
                break;
            case FLOAT:
                value = LLVM.LLVMConstReal(type, this.number.floatValue());
                break;
            case DOUBLE:
                value = LLVM.LLVMConstReal(type, this.number.doubleValue());
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + super.outputSlot.getType());
        }

        translatedMethod.getStack().buildStackStore(translatedMethod.getLlvmBuilder(), this.outputSlot, value);
    }
}
