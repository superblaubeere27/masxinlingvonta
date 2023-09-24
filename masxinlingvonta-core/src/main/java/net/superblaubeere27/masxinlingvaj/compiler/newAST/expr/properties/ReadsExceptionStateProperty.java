package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

public class ReadsExceptionStateProperty extends InstProperty {
    public static final ReadsExceptionStateProperty INSTANCE = new ReadsExceptionStateProperty();

    @Override
    public boolean conflictsWith(InstProperty other) {
        return other instanceof ThrowsProperty;
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
