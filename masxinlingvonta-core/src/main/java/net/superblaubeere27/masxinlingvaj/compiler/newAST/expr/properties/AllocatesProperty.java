package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

/**
 * Please don't repeat instruction marked with this as they allocate memory
 */
public class AllocatesProperty extends InstProperty {
    public static final AllocatesProperty INSTANCE = new AllocatesProperty();

    @Override
    public boolean conflictsWith(InstProperty other) {
        return false;
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
