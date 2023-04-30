package net.superblaubeere27.masxinlingvaj.compiler.newAST.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

import java.util.Objects;

public class StackLocal extends Local {
    private final int idx;
    private final boolean isLocal;
    private final ImmType type;

    public StackLocal(int idx, boolean isLocal, ImmType type) {
        this.idx = idx;
        this.isLocal = isLocal;
        this.type = type;
    }

    @Override
    public ImmType getType() {
        return this.type;
    }

    @Override
    public boolean isSSA() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StackLocal that = (StackLocal) o;
        return idx == that.idx && isLocal == that.isLocal && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idx, isLocal, type);
    }

    @Override
    public String toString() {
        return "%" + (this.isLocal ? 'l' : "s") + this.idx + this.type.getAssociatedCharacter();
    }
}
