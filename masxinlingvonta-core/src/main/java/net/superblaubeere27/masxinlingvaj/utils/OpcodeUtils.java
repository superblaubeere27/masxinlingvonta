package net.superblaubeere27.masxinlingvaj.utils;

import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.BinaryOperationInstruction;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

public class OpcodeUtils implements Opcodes {
    public static final Type OBJECT_TYPE = Type.getType("Ljava/lang/String;");
    private static final Type ARRAY_TYPE = Type.getType("[Ljava/lang/String;");

    public static Type getTypeOfInsnNode(OpcodeAnalysisContext analysisContext, AbstractInsnNode abstractInsnNode) {
        var opcode = abstractInsnNode.getOpcode();

        if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
            return Type.INT_TYPE;
        }

        switch (opcode) {
            case ACONST_NULL:
            case AALOAD:
            case ALOAD:
            case ASTORE:
            case AASTORE:
            case ARETURN:
                return OBJECT_TYPE;
            case IALOAD:
            case IASTORE:
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case INEG:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case L2I:
            case F2I:
            case D2I:
            case LCMP:
            case ARRAYLENGTH:
            case ILOAD:
            case IRETURN:
            case ISTORE:
                return Type.INT_TYPE;
            case LCONST_0:
            case LCONST_1:
            case LALOAD:
            case LASTORE:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LNEG:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
            case I2L:
            case F2L:
            case D2L:
            case LLOAD:
            case LRETURN:
            case LSTORE:
                return Type.LONG_TYPE;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case FALOAD:
            case FASTORE:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
            case FLOAD:
            case FRETURN:
            case FSTORE:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return Type.FLOAT_TYPE;
            case DCONST_0:
            case DCONST_1:
            case DALOAD:
            case DASTORE:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
            case DLOAD:
            case DRETURN:
            case DSTORE:
                return Type.DOUBLE_TYPE;
            case BALOAD:
            case BASTORE:
            case I2B:
                return Type.BYTE_TYPE;
            case CALOAD:
            case CASTORE:
            case I2C:
                return Type.CHAR_TYPE;
            case SALOAD:
            case SASTORE:
            case I2S:
                return Type.SHORT_TYPE;
            case RETURN:
            case ATHROW:
            case MONITORENTER:
            case MONITOREXIT:
                return Type.VOID_TYPE;
            case DUP:
            case DUP_X1:
            case DUP_X2:
            case DUP2:
            case DUP2_X1:
            case DUP2_X2:
                Frame<SourceValue> frame = analysisContext.getFrameOfInstruction(abstractInsnNode);

                return getReturnType(analysisContext, frame.getStack(frame.getStackSize() - 1).insns.stream().findFirst().get());
            default:
                throw new IllegalStateException("Unexpected value: " + opcode);
        }
    }

    public static Type getNewArrayType(IntInsnNode newArray) {
        switch (newArray.operand) {
            case T_BOOLEAN:
                return Type.BOOLEAN_TYPE;
            case T_CHAR:
                return Type.CHAR_TYPE;
            case T_FLOAT:
                return Type.FLOAT_TYPE;
            case T_DOUBLE:
                return Type.DOUBLE_TYPE;
            case T_BYTE:
                return Type.BYTE_TYPE;
            case T_SHORT:
                return Type.SHORT_TYPE;
            case T_INT:
                return Type.INT_TYPE;
            case T_LONG:
                return Type.LONG_TYPE;
            default:
                throw new IllegalStateException("Unexpected value: " + newArray.operand);
        }
    }

    public static Type getReturnType(OpcodeAnalysisContext analysisContext, AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) {
            return Type.getType(((FieldInsnNode) insn).desc);
        } else if (insn instanceof MethodInsnNode) {
            return Type.getReturnType(((MethodInsnNode) insn).desc);
        } else if (insn instanceof IntInsnNode) {
            return insn.getOpcode() == NEWARRAY ? Type.getType("[" + getNewArrayType((IntInsnNode) insn).getDescriptor()) : Type.INT_TYPE;
        } else if (insn instanceof MultiANewArrayInsnNode) {
            return Type.getType(((MultiANewArrayInsnNode) insn).desc);
        } else if (insn instanceof LdcInsnNode) {
            return getLdcType((LdcInsnNode) insn);
        } else if (insn instanceof TypeInsnNode) {
            if (insn.getOpcode() == NEW || insn.getOpcode() == CHECKCAST) {
                return Type.getType("L" + ((TypeInsnNode) insn).desc + ";");
            } else if (insn.getOpcode() == ANEWARRAY) {
                return Type.getType("[L" + ((TypeInsnNode) insn).desc + ";");
            } else if (insn.getOpcode() == INSTANCEOF) {
                return Type.BOOLEAN_TYPE;
            }
        } else if (insn instanceof InvokeDynamicInsnNode) {
            return Type.getReturnType(((InvokeDynamicInsnNode) insn).desc);
        } else if (insn instanceof VarInsnNode || insn instanceof InsnNode) {
            return getTypeOfInsnNode(analysisContext, insn);
        }
        throw new IllegalStateException("Unreachable");
    }

    public static BinaryOperationInstruction.BinaryOperationType getBinaryOperation(int opcode) {
        switch (opcode) {
            // Implement IALOAD - SALOAD
            case IADD:
            case LADD:
            case FADD:
            case DADD:
                return BinaryOperationInstruction.BinaryOperationType.ADD;
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
                return BinaryOperationInstruction.BinaryOperationType.SUB;
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
                return BinaryOperationInstruction.BinaryOperationType.MUL;
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
                return BinaryOperationInstruction.BinaryOperationType.DIV;
            case IREM:
            case LREM:
            case FREM:
            case DREM:
                return BinaryOperationInstruction.BinaryOperationType.REM;
            case ISHL:
            case LSHL:
                return BinaryOperationInstruction.BinaryOperationType.SHL;
            case ISHR:
            case LSHR:
                return BinaryOperationInstruction.BinaryOperationType.SHR;
            case IUSHR:
            case LUSHR:
                return BinaryOperationInstruction.BinaryOperationType.USHR;
            case IAND:
            case LAND:
                return BinaryOperationInstruction.BinaryOperationType.AND;
            case IOR:
            case LOR:
                return BinaryOperationInstruction.BinaryOperationType.OR;
            case IXOR:
            case LXOR:
                return BinaryOperationInstruction.BinaryOperationType.XOR;
            default:
                throw new IllegalStateException("Unexpected value: " + opcode);
        }
    }

    public static String getUnaryOperation(int opcode, String operand, String returnType) {
        switch (opcode) {
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
                return "return (" + returnType + ") " + operand + ";";
            case ATHROW:
                return "env->Throw(reinterpret_cast<jthrowable>(" + operand + "));";
            case ARRAYLENGTH:
                return "env->GetArrayLength(reinterpret_cast<jarray>(" + operand + "))";
            // Implement Array stores
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG:
                return "-" + operand;
            case I2L:
            case F2L:
            case D2L:
                return "(jlong) " + operand;
            case I2F:
            case L2F:
            case D2F:
                return "(jfloat) " + operand;
            case I2D:
                return "(double) " + operand;
            case L2I:
            case F2I:
            case D2I:
                return "(jint) " + operand;
            case L2D:
            case F2D:
                return "(jdouble) " + operand;
            case I2B:
                return "(jbyte) " + operand;
            case I2C:
                return "(jchar) " + operand;
            case I2S:
                return "(jshort) " + operand;
            default:
                throw new IllegalArgumentException("Not implemented: " + opcode);
        }
    }

    public static String getConstantName(int opcode) {
        switch (opcode) {
            case ACONST_NULL:
                return "(jobject) nullptr";
            case LCONST_0:
                return "0";
            case LCONST_1:
                return "1";
            case FCONST_0:
                return "0.0F";
            case FCONST_1:
                return "1.0F";
            case FCONST_2:
                return "2.0F";
            case DCONST_0:
                return "0.0";
            case DCONST_1:
                return "1.0";
            default:
                throw new IllegalStateException("Unexpected value: " + opcode);
        }
    }

    public static class OpcodeAnalysisContext {
        private final Frame<SourceValue>[] sourceValueFrames;
        private final InsnList insnList;

        public OpcodeAnalysisContext(Frame<SourceValue>[] sourceValueFrames, InsnList insnList) {
            this.sourceValueFrames = sourceValueFrames;
            this.insnList = insnList;
        }

        public Frame<SourceValue> getFrameOfInstruction(AbstractInsnNode insnNode) {
            return this.sourceValueFrames[insnList.indexOf(insnNode)];
        }
    }

    private static Type getLdcType(LdcInsnNode insn) {
        if (insn.cst instanceof Float) {
            return Type.FLOAT_TYPE;
        } else if (insn.cst instanceof Long) {
            return Type.LONG_TYPE;
        } else if (insn.cst instanceof Double) {
            return Type.DOUBLE_TYPE;
        } else if (insn.cst instanceof String) {
            return Type.getType("Ljava/lang/String;");
        } else if (insn.cst instanceof Integer) {
            return Type.INT_TYPE;
        } else if (insn.cst instanceof Type) {
            return Type.getType("Ljava/lang/Class;");
        }

        throw new IllegalStateException("Unreachable");
    }
}
