package net.superblaubeere27.masxinlingvaj.compiler.tree;

import org.objectweb.asm.tree.FieldNode;

public class CompilerField {
    private final CompilerClass parent;
    private final MethodOrFieldIdentifier identifier;
    private final FieldNode methodNode;

    CompilerField(CompilerClass cc, FieldNode methodNode) {
        this.parent = cc;
        this.identifier = new MethodOrFieldIdentifier(cc.getName(), methodNode.name, methodNode.desc);
        this.methodNode = methodNode;
    }

    public CompilerClass getParent() {
        return parent;
    }

    public FieldNode getNode() {
        return methodNode;
    }

    public MethodOrFieldIdentifier getIdentifier() {
        return identifier;
    }
}
