package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.ValueCreator;

import java.util.HashSet;
import java.util.Set;

public class SetCreator<T> implements ValueCreator<Set<T>> {
    private static final SetCreator<?> INSTANCE = new SetCreator<>();

    @SuppressWarnings("unchecked")
    public static <T> SetCreator<T> getInstance() {
        return (SetCreator<T>) INSTANCE;
    }

    @Override
    public Set<T> create() {
        return new HashSet<>();
    }
}
