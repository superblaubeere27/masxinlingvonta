package net.superblaubeere27.masxinlingvaj.compiler.graph;

import java.util.List;

public interface DepthFirstSearch<N> {

    List<N> getPreOrder();

    List<N> getPostOrder();

    List<N> getTopoOrder();
}
