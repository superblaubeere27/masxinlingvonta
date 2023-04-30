package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

/**
 * Please don't repeat instruction marked with this as they allocate memory
 */
public class AllocatesProperty extends ExprProperty {
    public static final AllocatesProperty INSTANCE = new AllocatesProperty();

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return false;
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
