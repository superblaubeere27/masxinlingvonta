package net.superblaubeere27.masxinlingvaj.preprocessor.codegen;

import org.objectweb.asm.Type;

import java.util.Arrays;

public enum TypeWrapper {
    //        wrapperType      simple     primitiveType  simple     char  emptyArray     format
    BOOLEAN(Type.BOOLEAN, "Boolean", "boolean", 'Z'),
    // These must be in the order defined for widening primitive conversions in JLS 5.1.2
    // Avoid boxing integral types here to defer initialization of internal caches
    BYTE(Type.BYTE, "Byte", "byte", 'B'),
    SHORT(Type.SHORT, "Short", "short", 'S'),
    CHAR(Type.CHAR, "Character", "char", 'C'),
    INT(Type.INT, "Integer", "int", 'I'),
    LONG(Type.LONG, "Long", "long", 'J'),
    FLOAT(Type.FLOAT, "Float", "float", 'F'),
    DOUBLE(Type.DOUBLE, "Double", "double", 'D'),
    OBJECT(Type.OBJECT, "Object", "Object", 'L'),
    // VOID must be the last type, since it is "assignable" from any other type:
    VOID(Type.VOID, "Void", "void", 'V'),
    ;

    public static final int COUNT = 10;

    private final int wrapperType;
    private final char basicTypeChar;
    private final String wrapperSimpleName;
    private final String primitiveSimpleName;

    TypeWrapper(int wtype, String wtypeName, String ptypeName, char tchar) {
        this.wrapperType = wtype;
        this.basicTypeChar = tchar;
        this.wrapperSimpleName = wtypeName;
        this.primitiveSimpleName = ptypeName;
    }

    public static TypeWrapper forPrimitiveType(Type arg) {
        return Arrays.stream(TypeWrapper.values()).filter(x -> arg.getSort() == x.wrapperType).findFirst().orElse(null);
    }

    public static TypeWrapper forBasicType(char c) {
        return Arrays.stream(TypeWrapper.values()).filter(x -> x.basicTypeChar == c).findFirst().orElse(null);
    }

    public boolean isSigned() {
        return this != CHAR;
    }

    public boolean isFloating() {
        return this == FLOAT || this == DOUBLE;
    }

    public char getBasicTypeChar() {
        return basicTypeChar;
    }

    public String getWrapperSimpleName() {
        return wrapperSimpleName;
    }

    public String getPrimitiveSimpleName() {
        return primitiveSimpleName;
    }
}
