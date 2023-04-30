package net.superblaubeere27.masxinlingvaj.compiler.tree;

import java.util.List;

public class VirtualMethodRelations {
    private final List<CompilerMethod> methodRoots;
    private final List<CompilerMethod> implementations;

    public VirtualMethodRelations(List<CompilerMethod> methodRoots, List<CompilerMethod> implementations) {
        this.methodRoots = methodRoots;
        this.implementations = implementations;
    }

    public List<CompilerMethod> getMethodRoots() {
        return methodRoots;
    }

    public List<CompilerMethod> getImplementations() {
        return implementations;
    }
}
