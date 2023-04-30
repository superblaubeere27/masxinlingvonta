package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.FloatingPointArithmeticsExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.IntegerArithmeticsExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PrimitiveCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class OpcodeUtils implements Opcodes {

    public static ImmType getLoadType(int opcode) {
        switch (opcode) {
            case ILOAD:
                return ImmType.INT;
            case LLOAD:
                return ImmType.LONG;
            case FLOAD:
                return ImmType.FLOAT;
            case DLOAD:
                return ImmType.DOUBLE;
            case ALOAD:
                return ImmType.OBJECT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static ImmType getStoreType(int opcode) {
        switch (opcode) {
            case ISTORE:
                return ImmType.INT;
            case LSTORE:
                return ImmType.LONG;
            case FSTORE:
                return ImmType.FLOAT;
            case DSTORE:
                return ImmType.DOUBLE;
            case ASTORE:
                return ImmType.OBJECT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static ImmType getValueReturnType(int opcode) {
        switch (opcode) {
            case IRETURN:
                return ImmType.INT;
            case LRETURN:
                return ImmType.LONG;
            case FRETURN:
                return ImmType.FLOAT;
            case DRETURN:
                return ImmType.DOUBLE;
            case ARETURN:
                return ImmType.OBJECT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static IntegerArithmeticsExpr.Operator getIntegerArithmeticsOperator(int opcode) {
        switch (opcode) {
            case IADD:
            case LADD:
                return IntegerArithmeticsExpr.Operator.ADD;
            case ISUB:
            case LSUB:
                return IntegerArithmeticsExpr.Operator.SUB;
            case IMUL:
            case LMUL:
                return IntegerArithmeticsExpr.Operator.MUL;
            case IDIV:
            case LDIV:
                return IntegerArithmeticsExpr.Operator.DIV;
            case IREM:
            case LREM:
                return IntegerArithmeticsExpr.Operator.REM;
            case ISHL:
            case LSHL:
                return IntegerArithmeticsExpr.Operator.SHL;
            case ISHR:
            case LSHR:
                return IntegerArithmeticsExpr.Operator.SHR;
            case IUSHR:
            case LUSHR:
                return IntegerArithmeticsExpr.Operator.USHR;
            case IAND:
            case LAND:
                return IntegerArithmeticsExpr.Operator.AND;
            case IOR:
            case LOR:
                return IntegerArithmeticsExpr.Operator.OR;
            case IXOR:
            case LXOR:
                return IntegerArithmeticsExpr.Operator.XOR;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static FloatingPointArithmeticsExpr.Operator getFloatingPointArithmeticsOperator(int opcode) {
        switch (opcode) {
            case FADD:
            case DADD:
                return FloatingPointArithmeticsExpr.Operator.ADD;
            case FSUB:
            case DSUB:
                return FloatingPointArithmeticsExpr.Operator.SUB;
            case FMUL:
            case DMUL:
                return FloatingPointArithmeticsExpr.Operator.MUL;
            case FDIV:
            case DDIV:
                return FloatingPointArithmeticsExpr.Operator.DIV;
            case FREM:
            case DREM:
                return FloatingPointArithmeticsExpr.Operator.REM;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static IntegerArithmeticsExpr.IntegerType getIntegerArithmeticsType(int opcode) {
        switch (opcode) {
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
                return IntegerArithmeticsExpr.IntegerType.INT;
            case LXOR:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LAND:
            case LUSHR:
            case LOR:
                return IntegerArithmeticsExpr.IntegerType.LONG;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static FloatingPointArithmeticsExpr.FloatingPointType getFloatingPointArithmeticsType(int opcode) {
        switch (opcode) {
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return FloatingPointArithmeticsExpr.FloatingPointType.FLOAT;
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return FloatingPointArithmeticsExpr.FloatingPointType.DOUBLE;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static JNIType getArrayLoadJNIType(int opcode) {
        switch (opcode) {
            case IALOAD:
                return JNIType.INT;
            case LALOAD:
                return JNIType.LONG;
            case FALOAD:
                return JNIType.FLOAT;
            case DALOAD:
                return JNIType.DOUBLE;
            case AALOAD:
                return JNIType.OBJECT;
            case BALOAD:
                return JNIType.BYTE;
            case CALOAD:
                return JNIType.CHAR;
            case SALOAD:
                return JNIType.SHORT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static JNIType getArrayStoreJNIType(int opcode) {
        switch (opcode) {
            case IASTORE:
                return JNIType.INT;
            case LASTORE:
                return JNIType.LONG;
            case FASTORE:
                return JNIType.FLOAT;
            case DASTORE:
                return JNIType.DOUBLE;
            case AASTORE:
                return JNIType.OBJECT;
            case BASTORE:
                return JNIType.BYTE;
            case CASTORE:
                return JNIType.CHAR;
            case SASTORE:
                return JNIType.SHORT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static ImmType getNegationType(int opcode) {
        switch (opcode) {
            case INEG:
                return ImmType.INT;
            case LNEG:
                return ImmType.LONG;
            case FNEG:
                return ImmType.FLOAT;
            case DNEG:
                return ImmType.DOUBLE;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static ImmType getCastInputType(int opcode) {
        switch (opcode) {
            case I2L:
            case I2F:
            case I2D:
            case I2B:
            case I2C:
            case I2S:
                return ImmType.INT;
            case L2I:
            case L2F:
            case L2D:
                return ImmType.LONG;
            case F2I:
            case F2L:
            case F2D:
                return ImmType.FLOAT;
            case D2I:
            case D2L:
            case D2F:
                return ImmType.DOUBLE;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static PrimitiveCastExpr.CastTarget getCastTarget(int opcode) {
        switch (opcode) {
            case I2B:
                return PrimitiveCastExpr.CastTarget.BYTE;
            case I2C:
                return PrimitiveCastExpr.CastTarget.CHAR;
            case I2S:
                return PrimitiveCastExpr.CastTarget.SHORT;
            case F2I:
            case L2I:
            case D2I:
                return PrimitiveCastExpr.CastTarget.INT;
            case I2L:
            case F2L:
            case D2L:
                return PrimitiveCastExpr.CastTarget.LONG;
            case L2F:
            case D2F:
            case I2F:
                return PrimitiveCastExpr.CastTarget.FLOAT;
            case I2D:
            case L2D:
            case F2D:
                return PrimitiveCastExpr.CastTarget.DOUBLE;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static IntegerCompareExpr.Operator getIntegerCompareType(int opcode) {
        switch (opcode) {
            case IFEQ:
            case IF_ICMPEQ:
                return IntegerCompareExpr.Operator.EQUAL;
            case IFNE:
            case IF_ICMPNE:
                return IntegerCompareExpr.Operator.NOT_EQUAL;
            case IFLT:
            case IF_ICMPLT:
                return IntegerCompareExpr.Operator.LOWER;
            case IFGE:
            case IF_ICMPGE:
                return IntegerCompareExpr.Operator.GREATER_EQUAL;
            case IFGT:
            case IF_ICMPGT:
                return IntegerCompareExpr.Operator.GREATER;
            case IFLE:
            case IF_ICMPLE:
                return IntegerCompareExpr.Operator.LOWER_EQUAL;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static boolean isObjectConvertInverted(int opcode) {
        switch (opcode) {
            case IF_ACMPEQ:
            case IFNULL:
                return false;
            case IF_ACMPNE:
            case IFNONNULL:
                return true;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static Type getNewArrayType(int typeIdx) {
        switch (typeIdx) {
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
                throw new IllegalStateException("Unexpected value: " + typeIdx);
        }
    }

    public static InvokeInstanceExpr.InvokeInstanceType getInvokeInstanceType(int opcode) {
        switch (opcode) {
            case INVOKEVIRTUAL:
                return InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL;
            case INVOKESPECIAL:
                return InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL;
            case INVOKEINTERFACE:
                return InvokeInstanceExpr.InvokeInstanceType.INVOKE_INTERFACE;
            default:
                throw new IllegalStateException("Unexpected value: " + opcode);
        }
    }
}
