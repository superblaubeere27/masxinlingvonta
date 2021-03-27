package net.superblaubeere27.masxinlingvaj.compiler.tree;

import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;

public class CompilerMethod {
    private final CompilerClass parent;
    private final MethodOrFieldIdentifier identifier;
    private final MethodNode methodNode;
    private boolean isMarkedForCompilation;
    private boolean wasCompiled;

    CompilerMethod(CompilerClass cc, MethodNode methodNode) {
        this.parent = cc;
        this.identifier = new MethodOrFieldIdentifier(cc.getName(), methodNode.name, methodNode.desc);
        this.methodNode = methodNode;
    }

    public CompilerClass getParent() {
        return parent;
    }

    public MethodNode getNode() {
        return methodNode;
    }

    public MethodOrFieldIdentifier getIdentifier() {
        return identifier;
    }

    public boolean isStatic() {
        return Modifier.isStatic(this.methodNode.access);
    }

    /**
     * Should only be called from {@link CompilerPreprocessor#markForCompilation(CompilerMethod)}
     */
    public void markForCompilation(CompilerPreprocessor preprocessor) {
        this.isMarkedForCompilation = true;
    }

    public void setWasCompiled() {
        this.parent.setModifiedFlag();

        this.wasCompiled = true;
    }

    public boolean wasCompiled() {
        return this.wasCompiled;
    }

    public boolean wasMarkedForCompilation() {
        return this.isMarkedForCompilation;
    }
}
