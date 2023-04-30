package net.superblaubeere27.masxinlingvaj.compiler.tree;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An indexed class
 */
public class CompilerClass {
    private final ClassNode classNode;
    private final List<CompilerMethod> methods;
    private final List<CompilerField> fields;
    private final boolean isLibrary;
    private boolean modifiedFlag;

    private ClassRelations relations;

    public CompilerClass(ClassNode classNode, boolean isLibrary) {
        this(classNode, isLibrary, false);
    }

    public CompilerClass(ClassNode classNode, boolean isLibrary, boolean wasSynthesized) {
        this.classNode = classNode;
        this.isLibrary = isLibrary;
        this.modifiedFlag = wasSynthesized;

        this.methods = classNode.methods.stream().map(x -> new CompilerMethod(this, x)).collect(Collectors.toList());
        this.fields = classNode.fields.stream().map(x -> new CompilerField(this, x)).collect(Collectors.toList());
    }

    public List<CompilerField> getFields() {
        return fields;
    }

    public List<CompilerMethod> getMethods() {
        return methods;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getName() {
        return this.classNode.name;
    }

    public String suggestStaticMethodName(String methodDesc) {
        var ref = new Object() {
            String currentName;
        };

        int i = 0;

        do {
            ref.currentName = "mlv$" + (i++);
        } while (methods.stream().anyMatch(x -> x.getNode().desc.equals(methodDesc) && x.getNode().name.equals(ref.currentName)));

        return ref.currentName;
    }

    /**
     * Adds a method to this and the presented class
     */
    public void addMethod(MLVCompiler compiler, MethodNode extractedMethod) {
        this.classNode.methods.add(extractedMethod);

        this.methods.add(new CompilerMethod(this, extractedMethod));

        compiler.getIndex().refreshClass(this);
    }

    public void setModifiedFlag() {
        if (this.isLibrary)
            throw new IllegalStateException("Library class \"" + this.getName() + "\" cannot be modified");

        this.modifiedFlag = true;
    }

    public boolean getModifiedFlag() {
        return this.modifiedFlag;
    }

    public ClassRelations getRelations() {
        return relations;
    }

    public void setRelations(ClassRelations relations) {
        this.relations = relations;
    }

    public boolean isLibrary() {
        return isLibrary;
    }

    public boolean isInterface() {
        return (this.classNode.access & Opcodes.ACC_INTERFACE) != 0;
    }
}
