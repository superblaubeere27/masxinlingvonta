package net.superblaubeere27.masxinlingvaj.compiler.tree;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Objects;

public class MethodOrFieldIdentifier {
    private final String owner;
    private final String name;
    private final String desc;

    /**
     * Caches the hash code
     */
    private int hashCode = -1;

    public MethodOrFieldIdentifier(FieldInsnNode fieldInsnNode) {
        this(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc);
    }

    public MethodOrFieldIdentifier(MethodInsnNode fieldInsnNode) {
        this(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc);
    }

    public MethodOrFieldIdentifier(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public MethodOrFieldIdentifier(Handle handle) {
        this(handle.getOwner(), handle.getName(), handle.getDesc());
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodOrFieldIdentifier that = (MethodOrFieldIdentifier) o;
        return hashCode == that.hashCode && Objects.equals(owner, that.owner) && Objects.equals(name,
                that.name) && Objects.equals(desc, that.desc);
    }

    @Override
    public int hashCode() {
        // The hash might be -1, but that is that much of a problem
        if (this.hashCode != -1) {
            return this.hashCode;
        }

        this.hashCode = Objects.hash(owner, name, desc, hashCode);

        return this.hashCode;
    }

    @Override
    public String toString() {
        return owner + "." + this.name + this.desc;
    }
}