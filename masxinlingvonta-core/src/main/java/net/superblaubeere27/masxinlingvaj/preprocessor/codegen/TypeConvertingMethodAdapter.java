package net.superblaubeere27.masxinlingvaj.preprocessor.codegen;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static net.superblaubeere27.masxinlingvaj.preprocessor.codegen.TypeWrapper.*;


public class TypeConvertingMethodAdapter extends MethodVisitor {
    private static final int NUM_WRAPPERS = TypeWrapper.COUNT;

    private static final String NAME_OBJECT = "java/lang/Object";
    private static final String WRAPPER_PREFIX = "Ljava/lang/";

    // Same for all primitives; name of the boxing method
    private static final String NAME_BOX_METHOD = "valueOf";

    // Table of opcodes for widening primitive conversions; NOP = no conversion
    private static final int[][] wideningOpcodes = new int[NUM_WRAPPERS][NUM_WRAPPERS];

    private static final TypeWrapper[] FROM_WRAPPER_NAME = new TypeWrapper[16];

    // Table of wrappers for primitives, indexed by ASM type sorts
    private static final TypeWrapper[] FROM_TYPE_SORT = new TypeWrapper[12];

    static {
        for (TypeWrapper w : TypeWrapper.values()) {
            if (w.getBasicTypeChar() != 'L') {
                int wi = hashWrapperName(w.getWrapperSimpleName());

                FROM_WRAPPER_NAME[wi] = w;
            }
        }

        initWidening(LONG, Opcodes.I2L, BYTE, SHORT, INT, CHAR);
        initWidening(LONG, Opcodes.F2L, FLOAT);
        initWidening(FLOAT, Opcodes.I2F, BYTE, SHORT, INT, CHAR);
        initWidening(FLOAT, Opcodes.L2F, LONG);
        initWidening(DOUBLE, Opcodes.I2D, BYTE, SHORT, INT, CHAR);
        initWidening(DOUBLE, Opcodes.F2D, FLOAT);
        initWidening(DOUBLE, Opcodes.L2D, LONG);

        FROM_TYPE_SORT[Type.BYTE] = TypeWrapper.BYTE;
        FROM_TYPE_SORT[Type.SHORT] = TypeWrapper.SHORT;
        FROM_TYPE_SORT[Type.INT] = TypeWrapper.INT;
        FROM_TYPE_SORT[Type.LONG] = TypeWrapper.LONG;
        FROM_TYPE_SORT[Type.CHAR] = TypeWrapper.CHAR;
        FROM_TYPE_SORT[Type.FLOAT] = TypeWrapper.FLOAT;
        FROM_TYPE_SORT[Type.DOUBLE] = TypeWrapper.DOUBLE;
        FROM_TYPE_SORT[Type.BOOLEAN] = TypeWrapper.BOOLEAN;
    }

    public TypeConvertingMethodAdapter(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    private static void initWidening(TypeWrapper to, int opcode, TypeWrapper... from) {
        for (TypeWrapper f : from) {
            wideningOpcodes[f.ordinal()][to.ordinal()] = opcode;
        }
    }

    private static int hashWrapperName(String xn) {
        if (xn.length() < 3) {
            return 0;
        }
        return (3 * xn.charAt(1) + xn.charAt(2)) % 16;
    }

    static String wrapperName(TypeWrapper w) {
        return "java/lang/" + w.getWrapperSimpleName();
    }

    private static String boxingDescriptor(TypeWrapper w) {
        return "(" + w.getBasicTypeChar() + ")L" + wrapperName(w) + ";";
    }

    private static String unboxingDescriptor(TypeWrapper w) {
        return "()" + w.getBasicTypeChar();
    }

    private static String unboxMethod(TypeWrapper w) {
        return w.getPrimitiveSimpleName() + "Value";
    }

    protected TypeWrapper wrapperOrNullFromDescriptor(String desc) {
        if (!desc.startsWith(WRAPPER_PREFIX)) {
            // Not a class type (array or method), so not a boxed type
            // or not in the right package
            return null;
        }

        // Pare it down to the simple class name
        String cname = desc.substring(WRAPPER_PREFIX.length(), desc.length() - 1);
        // Hash to a Wrapper
        TypeWrapper w = FROM_WRAPPER_NAME[hashWrapperName(cname)];

        if (w == null || w.getWrapperSimpleName().equals(cname)) {
            return w;
        } else {
            return null;
        }
    }

    void widen(TypeWrapper ws, TypeWrapper wt) {
        if (ws != wt) {
            int opcode = wideningOpcodes[ws.ordinal()][wt.ordinal()];
            if (opcode != Opcodes.NOP) {
                visitInsn(opcode);
            }
        }
    }

    void box(TypeWrapper w) {
        visitMethodInsn(Opcodes.INVOKESTATIC,
                wrapperName(w),
                NAME_BOX_METHOD,
                boxingDescriptor(w), false);
    }

    void cast(String ds, String dt) {
        if (!dt.equals(ds) && !dt.equals(NAME_OBJECT)) {
            visitTypeInsn(Opcodes.CHECKCAST, dt);
        }
    }

    TypeWrapper toWrapper(String desc) {
        char first = desc.charAt(0);
        if (first == '[' || first == '(') {
            first = 'L';
        }
        return TypeWrapper.forBasicType(first);
    }

    void unbox(String sname, TypeWrapper wt) {
        visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                sname,
                unboxMethod(wt),
                unboxingDescriptor(wt), false);
    }
}
