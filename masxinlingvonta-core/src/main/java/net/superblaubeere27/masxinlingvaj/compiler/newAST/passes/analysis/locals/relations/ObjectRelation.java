package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import java.util.Objects;

/**
 * Basically says the subject is equals/not equals (based on <code>equals</code>) to <code>rhs</code>
 */
public class ObjectRelation extends Assumption {
    private final RelationObject<Object> rhs;
    private final boolean equals;

    public ObjectRelation(RelationObject<Object> rhs, boolean equals) {
        this.rhs = rhs;
        this.equals = equals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectRelation that = (ObjectRelation) o;
        return equals == that.equals && Objects.equals(rhs, that.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rhs, equals);
    }

    @Override
    public String toString() {
        return this.equals ? "equals " : "not-equals " + this.rhs.toString();
    }
}
