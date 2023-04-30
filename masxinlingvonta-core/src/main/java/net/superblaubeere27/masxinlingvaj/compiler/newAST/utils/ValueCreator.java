package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

public interface ValueCreator<V> extends KeyedValueCreator<Object, V> {
    V create();

    @Override
    default V create(Object o) {
        return create();
    }
}