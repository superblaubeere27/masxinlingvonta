package net.superblaubeere27.masxinlingvaj.compiler.tree;

import java.util.Objects;

public class MethodOrFieldName {
    private final String name;
    private final String desc;

    /**
     * Caches the hash code
     */
    private int hashCode = -1;

    public MethodOrFieldName(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public MethodOrFieldName(MethodOrFieldIdentifier identifier) {
        this(identifier.getName(), identifier.getDesc());
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
        MethodOrFieldName that = (MethodOrFieldName) o;
        return Objects.equals(name, that.name) && Objects.equals(desc, that.desc);
    }

    @Override
    public int hashCode() {
        if (this.hashCode != -1) {
            return this.hashCode;
        }

        return this.hashCode = Objects.hash(name, desc);
    }
}
