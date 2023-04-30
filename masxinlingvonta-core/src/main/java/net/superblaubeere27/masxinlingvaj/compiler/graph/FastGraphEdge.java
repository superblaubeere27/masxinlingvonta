package net.superblaubeere27.masxinlingvaj.compiler.graph;

public interface FastGraphEdge<N extends FastGraphVertex> {
    N src();

    N dst();
}
