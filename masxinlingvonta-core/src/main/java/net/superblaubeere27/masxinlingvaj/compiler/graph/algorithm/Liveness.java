package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;

import java.util.Set;

public interface Liveness<N> {

    Set<Local> in(N n);

    Set<Local> out(N n);
}