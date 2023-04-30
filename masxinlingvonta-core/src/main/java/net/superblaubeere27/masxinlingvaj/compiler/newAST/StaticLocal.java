package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import java.util.Objects;

public class StaticLocal extends Local {
    private final int idx;
    private final ImmType type;

    public StaticLocal(int idx, ImmType type) {
        this.idx = idx;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StaticLocal that = (StaticLocal) o;
        return idx == that.idx && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idx, type);
    }

    @Override
    public String toString() {
        return "%" + this.idx + this.type.getAssociatedCharacter();
    }

    @Override
    public ImmType getType() {
        return this.type;
    }

    @Override
    public boolean isSSA() {
        return true;
    }
}
