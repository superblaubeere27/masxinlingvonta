package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

public class LocksProperty extends ExprProperty {

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return other instanceof ThrowsProperty || other instanceof WritesMemoryProperty;
    }

    @Override
    public boolean changesState() {
        return true;
    }

}
