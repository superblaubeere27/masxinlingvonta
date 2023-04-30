package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

public abstract class LocalInfo {

    public abstract LocalInfo merge(LocalInfo other);

    public abstract boolean equivalent(LocalInfo other);

}
