package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

public class ChangesLocalProperty extends ExprProperty {
    private final Local target;

    public ChangesLocalProperty(Local target) {
        this.target = target;
    }

    public Local getTarget() {
        return target;
    }

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return other instanceof ReadsLocalProperty && (this.target.equals(((ReadsLocalProperty) other).getTarget()) || ((ReadsLocalProperty) other).getTarget() == null);
    }

    @Override
    public boolean changesState() {
        return true;
    }
}
