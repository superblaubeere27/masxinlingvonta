package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

public class LocksProperty extends InstProperty {

    @Override
    public boolean conflictsWith(InstProperty other) {
        return other instanceof ThrowsProperty || other instanceof WritesMemoryProperty || other instanceof ReadsMemoryProperty;
    }

    @Override
    public boolean changesState() {
        return true;
    }

}
