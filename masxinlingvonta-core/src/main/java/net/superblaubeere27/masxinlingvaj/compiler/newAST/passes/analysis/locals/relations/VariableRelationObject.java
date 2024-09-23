package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;

import java.util.Objects;

public class VariableRelationObject<T> extends RelationObject<T> {
    private final Local variable;
    private final Stmt declaringStatement;

    public VariableRelationObject(Local variable, Stmt declaringStatement) {
        this.variable = variable;
        this.declaringStatement = declaringStatement;
    }

    public Local getVariable() {
        return variable;
    }

    public Stmt getDeclaringStatement() {
        return declaringStatement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableRelationObject<?> that = (VariableRelationObject<?>) o;
        return Objects.equals(variable, that.variable) && Objects.equals(declaringStatement, that.declaringStatement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, declaringStatement);
    }

    @Override
    public String toString() {
        return this.variable.toString();
    }
}
