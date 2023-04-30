package net.superblaubeere27.masxinlingvaj.compiler.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface FastGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> {

    Set<N> vertices();

    boolean addVertex(N n);

    void removeVertex(N n);

    boolean containsVertex(N n);

    void addEdge(E e);

    void removeEdge(E e);

    boolean containsEdge(E e);

    Set<E> getEdges(N n);

    int size();

    default E clone(E edge, N newSrc, N newDst) {
        throw new UnsupportedOperationException();
    }

    void replace(N old, N n);

    void clear();

    default FastGraph<N, E> copy() {
        throw new UnsupportedOperationException();
    }

    default Map<N, Set<E>> createMap() {
        return new HashMap<>();
    }

    default Map<N, Set<E>> createMap(Map<N, Set<E>> map) {
        Map<N, Set<E>> map2 = createMap();
        for (Map.Entry<N, Set<E>> e : map.entrySet()) {
            map2.put(e.getKey(), createSet(e.getValue()));
        }
        return map2;
    }

    default Set<E> createSet() {
        return new HashSet<>();
    }

    default Set<E> createSet(Set<E> set) {
        Set<E> newSet = createSet();
        newSet.addAll(set);
        return newSet;
    }
}
