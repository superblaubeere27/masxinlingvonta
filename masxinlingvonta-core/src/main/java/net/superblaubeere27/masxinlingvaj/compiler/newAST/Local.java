package net.superblaubeere27.masxinlingvaj.compiler.newAST;

public abstract class Local {
    public abstract ImmType getType();

    /**
     * Does this variable follow the SSA scheme?
     */
    public abstract boolean isSSA();

    /**
     * All locals implement equals.
     */
    public abstract boolean equals(Object other);
}
