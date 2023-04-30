package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import java.util.HashMap;

public class IncrementalBitSetIndexer<N> implements BitSetIndexer<N> {

    private final NullPermeableHashMap<N, Integer> map;
    private final HashMap<Integer, N> reverseMap;

    public IncrementalBitSetIndexer() {
        map = new NullPermeableHashMap<>(new ValueCreator<Integer>() {
            @Override
            public Integer create() {
                return map.size() + 1;
            }
        });
        reverseMap = new HashMap<>();
    }

    @Override
    public int getIndex(N n) {
        int index = map.getNonNull(n);
        reverseMap.put(index, n);
        return index;
    }

    @Override
    public N get(int index) {
        return reverseMap.get(index);
    }

    @Override
    public boolean isIndexed(N o) {
        return map.containsKey(o);
    }
}
