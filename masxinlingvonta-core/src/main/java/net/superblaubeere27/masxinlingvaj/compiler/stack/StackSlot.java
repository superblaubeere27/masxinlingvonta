package net.superblaubeere27.masxinlingvaj.compiler.stack;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;

import java.util.Objects;

public class StackSlot {
    private final JNIType type;
    private final int index;

    public StackSlot(JNIType type, int index) {
        if (type == JNIType.VOID || type == JNIType.SHORT || type == JNIType.BYTE || type == JNIType.CHAR || type == JNIType.BOOLEAN)
            throw new IllegalArgumentException("A stack slot cannot have the type " + type);
        if (index < 0)
            throw new IllegalArgumentException("Invalid stack index " + index);

        this.type = type;
        this.index = index;
    }

    public JNIType getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StackSlot stackSlot = (StackSlot) o;
        return index == stackSlot.index && type == stackSlot.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index);
    }
}
