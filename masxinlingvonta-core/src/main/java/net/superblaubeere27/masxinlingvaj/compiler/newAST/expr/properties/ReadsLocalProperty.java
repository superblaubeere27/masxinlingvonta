package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

public class ReadsLocalProperty extends ExprProperty {
    private final Local target;

    public ReadsLocalProperty(Local target) {
        this.target = target;
    }

    public Local getTarget() {
        return target;
    }

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return other instanceof ChangesLocalProperty && (this.target.equals(((ChangesLocalProperty) other).getTarget()) || ((ChangesLocalProperty) other).getTarget() == null);
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
