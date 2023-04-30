package net.superblaubeere27.masxinlingvaj.compiler.graph;

public interface FlowEdge<N extends FastGraphVertex> extends FastGraphEdge<N> {

    int getType();

    String toGraphString();

    @Override
    String toString();

    String toInverseString();

    FlowEdge<N> clone(N src, N dst);
}
