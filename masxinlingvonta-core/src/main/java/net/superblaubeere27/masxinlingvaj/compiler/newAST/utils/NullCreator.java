package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

public class NullCreator<V> implements ValueCreator<V> {

    @Override
    public V create() {
        return null;
    }
}