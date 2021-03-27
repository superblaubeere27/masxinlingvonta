package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Opcodes;

public class PrimitiveCastInstruction extends Instruction implements Opcodes {
    private final StackSlot inputSlot;
    private final StackSlot outputSlot;
    private final JNIType inType;
    private final JNIType outType;
    private final CastOperation castOperation;

    private PrimitiveCastInstruction(StackSlot inputSlot, StackSlot outputSlot, JNIType inType, JNIType outType, CastOperation castOperation) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.inType = inType;
        this.outType = outType;
        this.castOperation = castOperation;
    }

    public static PrimitiveCastInstruction fromOpcode(int opcode, int stackDepth) {
        var idx = stackDepth - 1;

        switch (opcode) {
            case I2L: // visitInsn
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.INT, idx),
                        JNIType.INT,
                        JNIType.LONG,
                        CastOperation.INT_CAST);
            case I2F: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.FLOAT, idx),
                        JNIType.INT,
                        JNIType.FLOAT,
                        CastOperation.I_TO_FP);
            case I2D: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.DOUBLE, idx),
                        JNIType.INT,
                        JNIType.DOUBLE,
                        CastOperation.I_TO_FP);
            case L2I: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.LONG, idx),
                        new StackSlot(JNIType.INT, idx),
                        JNIType.LONG,
                        JNIType.INT,
                        CastOperation.INT_CAST);
            case L2F: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.LONG, idx),
                        new StackSlot(JNIType.FLOAT, idx),
                        JNIType.LONG,
                        JNIType.FLOAT,
                        CastOperation.I_TO_FP);
            case L2D: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.LONG, idx),
                        new StackSlot(JNIType.DOUBLE, idx),
                        JNIType.LONG,
                        JNIType.DOUBLE,
                        CastOperation.I_TO_FP);
            case F2I: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.FLOAT, idx),
                        new StackSlot(JNIType.INT, idx),
                        JNIType.FLOAT,
                        JNIType.INT,
                        CastOperation.FP_TO_I);
            case F2L: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.FLOAT, idx),
                        new StackSlot(JNIType.LONG, idx),
                        JNIType.FLOAT,
                        JNIType.LONG,
                        CastOperation.FP_TO_I);
            case F2D: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.FLOAT, idx),
                        new StackSlot(JNIType.DOUBLE, idx),
                        JNIType.FLOAT,
                        JNIType.DOUBLE,
                        CastOperation.FLOAT_CAST);
            case D2I: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.DOUBLE, idx),
                        new StackSlot(JNIType.INT, idx),
                        JNIType.DOUBLE,
                        JNIType.INT,
                        CastOperation.FP_TO_I);
            case D2L: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.DOUBLE, idx),
                        new StackSlot(JNIType.LONG, idx),
                        JNIType.DOUBLE,
                        JNIType.LONG,
                        CastOperation.FP_TO_I);
            case D2F: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.DOUBLE, idx),
                        new StackSlot(JNIType.FLOAT, idx),
                        JNIType.DOUBLE,
                        JNIType.FLOAT,
                        CastOperation.FLOAT_CAST);
            case I2B: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.BYTE, idx),
                        JNIType.INT,
                        JNIType.BYTE,
                        CastOperation.INT_CAST);
            case I2C: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.CHAR, idx),
                        JNIType.INT,
                        JNIType.CHAR,
                        CastOperation.INT_CAST);
            case I2S: // -
                return new PrimitiveCastInstruction(new StackSlot(JNIType.INT, idx),
                        new StackSlot(JNIType.SHORT, idx),
                        JNIType.INT,
                        JNIType.SHORT,
                        CastOperation.INT_CAST);
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();
        var operand = translatedMethod.getStack().buildStackLoad(builder, this.inputSlot);

        LLVMValueRef result;

        switch (this.castOperation) {
            case INT_CAST:
                result = LLVM.LLVMBuildIntCast(builder, operand, this.outType.getLLVMType(), "");
                break;
            case FLOAT_CAST:
                result = LLVM.LLVMBuildFPCast(builder, operand, this.outType.getLLVMType(), "");
                break;
            case FP_TO_I:
                result = LLVM.LLVMBuildFPToSI(builder, operand, this.outType.getLLVMType(), "");
                break;
            case I_TO_FP:
                result = LLVM.LLVMBuildSIToFP(builder, operand, this.outType.getLLVMType(), "");
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.castOperation);
        }

        translatedMethod.getStack().buildStackStore(builder, this.outputSlot, result);
    }

    private enum CastOperation {
        INT_CAST,
        FLOAT_CAST,
        FP_TO_I,
        I_TO_FP
    }
}
