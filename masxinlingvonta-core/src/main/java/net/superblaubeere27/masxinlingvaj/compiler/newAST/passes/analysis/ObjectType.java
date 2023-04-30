package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import org.objectweb.asm.Type;

public class ObjectType {
    private final String type;

    public ObjectType(String type) {
        if (type == null || type.isEmpty())
            throw new IllegalStateException("Type is null or empty!");

        this.type = type;
    }

    public String getTypeOfObject() {
        if (this.isArray())
            throw new IllegalStateException("Tried to retrieve the object type of an array");

        return type;
    }

    public String getTypeOfObjectOrArray() {
        return type;
    }

    public boolean isArray() {
        return this.type.charAt(0) == '[';
    }

    public boolean isObject() {
        return !this.isArray();
    }

    @Override
    public String toString() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectType that = (ObjectType) o;

        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    public Type getArrayElementType() {
        if (!this.isArray())
            throw new IllegalStateException("Tried to get element type of array");

        return Type.getType(this.type.substring(1));
    }
}
