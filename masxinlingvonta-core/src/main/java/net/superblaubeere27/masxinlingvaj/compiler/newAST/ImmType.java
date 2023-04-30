package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import org.objectweb.asm.Type;

public enum ImmType {
    VOID(0, 'V', null),
    BOOL(0, 'Z', null),
    INT(1, 'I', new ConstIntExpr(0)),
    LONG(2, 'J', new ConstLongExpr(0)),
    FLOAT(1, 'F', new ConstFloatExpr(0.0F)),
    DOUBLE(2, 'D', new ConstDoubleExpr(0.0D)),
    OBJECT(1, 'A', new ConstNullExpr());

    private final int jvmStackSize;
    private final char associatedCharacter;
    private final Expr constNull;

    ImmType(int jvmStackSize, char associatedCharacter, Expr constNull) {
        this.jvmStackSize = jvmStackSize;
        this.associatedCharacter = associatedCharacter;
        this.constNull = constNull;
    }

    public static ImmType fromJVMType(Type jvmType) {
        switch (jvmType.getSort()) {
            case 0:
                return VOID;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return INT;
            case 6:
                return FLOAT;
            case 7:
                return LONG;
            case 8:
                return DOUBLE;
            case 9:
            case 10:
                return OBJECT;
            default:
                throw new IllegalArgumentException("Unsupported type exception " + jvmType);
        }
    }

    public static ImmType fromJNIType(JNIType jniType) {
        switch (jniType) {
            case VOID:
                return VOID;
            case BOOLEAN:
            case CHAR:
            case BYTE:
            case SHORT:
            case INT:
                return INT;
            case LONG:
                return LONG;
            case FLOAT:
                return FLOAT;
            case DOUBLE:
                return DOUBLE;
            case OBJECT:
                return OBJECT;
            default:
                throw new IllegalArgumentException("Unsupported type exception " + jniType);
        }
    }

    public JNIType toNativeType() {
        switch (this) {
            case INT:
                return JNIType.INT;
            case LONG:
                return JNIType.LONG;
            case FLOAT:
                return JNIType.FLOAT;
            case DOUBLE:
                return JNIType.DOUBLE;
            case OBJECT:
                return JNIType.OBJECT;
            default:
                throw new IllegalStateException("Invalid JNI-Type: " + this);
        }
    }

    public int getJvmStackSize() {
        return jvmStackSize;
    }

    public char getAssociatedCharacter() {
        return associatedCharacter;
    }

    public Expr createConstNull() {
        if (this.constNull == null)
            throw new IllegalArgumentException("Cannot create a const null for an empty type");

        return this.constNull.copy();
    }

    public boolean isInteger() {
        switch (this) {
            case VOID:
            case BOOL:
            case FLOAT:
            case DOUBLE:
            case OBJECT:
                return false;
            case INT:
            case LONG:
                return true;
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }

    public boolean isFloat() {
        switch (this) {
            case VOID:
            case BOOL:
            case OBJECT:
            case INT:
            case LONG:
                return false;
            case FLOAT:
            case DOUBLE:
                return true;
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
