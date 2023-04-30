package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

public abstract class ExprProperty {

    /**
     * Can an expression with this property be moved after another
     * expression with the given property
     */
    public abstract boolean conflictsWith(ExprProperty other);

    /**
     * When this returns true, the annotated instruction might
     * change the state of the program and cannot be removed.
     */
    public abstract boolean changesState();

}
