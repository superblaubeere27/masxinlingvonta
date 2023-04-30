package net.superblaubeere27.masxinlingvaj.compiler.graph;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.BitSetIndexer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.GenericBitSet;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.ValueCreator;

import java.util.*;

public abstract class FlowGraph<N extends FastGraphVertex, E extends FlowEdge<N>> extends FastDirectedGraph<N, E> implements ValueCreator<GenericBitSet<N>> {

    protected final Set<N> entries;

    protected final BitSetIndexer<N> indexer;
    protected final Map<Integer, N> indexMap;
    protected final BitSet indexedSet;

    public FlowGraph() {
        entries = new HashSet<>();

        indexer = new FastGraphVertexBitSetIndexer();
        indexMap = new HashMap<>();
        indexedSet = new BitSet();
    }

    public FlowGraph(FlowGraph<N, E> g) {
        super(g);

        entries = new HashSet<>(g.entries);

        indexer = g.indexer;
        indexMap = new HashMap<>(g.indexMap);
        indexedSet = g.indexedSet;
    }

    public Set<N> getEntries() {
        return entries;
    }

    @Override
    public void clear() {
        super.clear();
        indexMap.clear();
        indexedSet.clear();
    }

    @Override
    public boolean addVertex(N v) {
        boolean ret = super.addVertex(v);

        int index = v.getNumericId();
        assert (!indexMap.containsKey(index) || indexMap.get(index) == v); // ensure no id collisions
        indexMap.put(index, v);
        indexedSet.set(index, true);
        return ret;
    }

    @Override
    public void addEdge(E e) {
        super.addEdge(e);

        N src = e.src();
        int index = src.getNumericId();
        assert (!indexMap.containsKey(index) || indexMap.get(index) == src); // ensure no id collisions
        indexMap.put(index, src);
        indexedSet.set(index, true);
    }

    @Override
    public void replace(N old, N n) {
        if (entries.contains(old)) {
            entries.add(n);
        }
        super.replace(old, n);
    }

    @Override
    public void removeVertex(N v) {
        entries.remove(v);
        super.removeVertex(v);

        int index = v.getNumericId();
        indexMap.remove(index);
        indexedSet.set(index, false);
    }

    public List<N> verticesInOrder() {
        return SimpleDfs.topoorder(this, getEntries().iterator().next());
    }

    // this is some pretty bad code duplication but it's not too big of a deal.
    public Set<N> dfsNoHandlers(N from, N to) {
        Set<N> visited = new HashSet<>();
        Deque<N> stack = new ArrayDeque<>();
        stack.push(from);

        while (!stack.isEmpty()) {
            N s = stack.pop();

            Set<E> edges = getEdges(s);
            for (FlowEdge<N> e : edges) {
                N next = e.dst();

                if (next != to && !visited.contains(next)) {
                    stack.push(next);
                    visited.add(next);
                }
            }
        }

        visited.add(from);

        return visited;
    }

    public GenericBitSet<N> createBitSet() {
        return new GenericBitSet<>(indexer);
    }

    public GenericBitSet<N> createBitSet(Collection<N> other) {
        GenericBitSet<N> set = createBitSet();
        set.addAll(other);
        return set;
    }

    @Override
    public GenericBitSet<N> create() {
        return createBitSet();
    }

    private class FastGraphVertexBitSetIndexer implements BitSetIndexer<N> {
        @Override
        public int getIndex(N basicBlock) {
            return basicBlock.getNumericId();
        }

        @Override
        public N get(int index) {
            // really, we don't want to be using this since it pretty much defeats the point of the whole bitset scheme.
            return indexMap.get(index);
        }

        @Override
        public boolean isIndexed(N basicBlock) {
            return basicBlock != null && indexedSet.get(getIndex(basicBlock));
        }
    }
}
