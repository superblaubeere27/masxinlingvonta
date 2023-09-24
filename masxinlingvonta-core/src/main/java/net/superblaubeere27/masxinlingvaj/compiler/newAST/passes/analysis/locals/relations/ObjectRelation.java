package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

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
    public boolean equivalent(Assumption other) {
        return other instanceof ObjectRelation objectRelation && this.rhs.equals(objectRelation.rhs) && this.equals == objectRelation.equals;
    }

    @Override
    public String toString() {
        return this.equals ? "equals " : "not-equals " + this.rhs.toString();
    }
}
