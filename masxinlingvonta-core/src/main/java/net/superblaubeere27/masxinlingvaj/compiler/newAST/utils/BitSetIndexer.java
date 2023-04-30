package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

public interface BitSetIndexer<N> {
    int getIndex(N n);

    N get(int index);

    boolean isIndexed(N o);
}
