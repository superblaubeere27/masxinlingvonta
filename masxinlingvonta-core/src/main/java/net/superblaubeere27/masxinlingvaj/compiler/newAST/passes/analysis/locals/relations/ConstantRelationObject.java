package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import java.util.Objects;

public class ConstantRelationObject<O> extends RelationObject<O> {
    private final O subject;

    public ConstantRelationObject(O subject) {
        this.subject = subject;
    }

    public O getSubject() {
        return subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstantRelationObject<?> that = (ConstantRelationObject<?>) o;

        return Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return subject != null ? subject.hashCode() : 0;
    }

    @Override
    public String toString() {
        return this.subject.toString();
    }
}
