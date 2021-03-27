package net.superblaubeere27.masxinlingvaj.preprocessor.codegen;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;

public class PreGeneratedCallSite {
    private final Handle handle;
    private final ClassNode classNode;

    public PreGeneratedCallSite(Handle handle, ClassNode classNode) {
        this.handle = handle;
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public Handle getHandle() {
        return handle;
    }
}
