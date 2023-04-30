package net.superblaubeere27.masxinlingvaj.compiler.tree;

import java.util.List;

public class ClassRelations {
    private final CompilerClass superClass;
    private final List<CompilerClass> parentClasses;
    private final List<CompilerClass> subClasses;

    public ClassRelations(CompilerClass superClass, List<CompilerClass> parentClasses, List<CompilerClass> subClasses) {
        this.superClass = superClass;
        this.parentClasses = parentClasses;
        this.subClasses = subClasses;
    }

    public CompilerClass getSuperClass() {
        return superClass;
    }

    public List<CompilerClass> getSubClasses() {
        return subClasses;
    }

    public List<CompilerClass> getParentClasses() {
        return parentClasses;
    }
}
