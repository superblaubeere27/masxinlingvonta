package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import java.util.HashMap;

public class NullPermeableHashMap<K, V> extends HashMap<K, V> {

    private static final long serialVersionUID = 1L;

    private final KeyedValueCreator<? super K, ? extends V> creator;

    public NullPermeableHashMap(NullPermeableHashMap<K, V> map) {
        super(map);
        creator = map.creator;
    }

    public NullPermeableHashMap(KeyedValueCreator<? super K, ? extends V> creator) {
        this.creator = creator;
    }

    public NullPermeableHashMap(ValueCreator<? extends V> creator) {
        this((KeyedValueCreator<? super K, ? extends V>) creator);
    }

    public NullPermeableHashMap() {
        this(new NullCreator<>());
    }

    public V getNonNull(K k) {
        return computeIfAbsent(k, creator::create);
    }
}