package net.superblaubeere27.masxinlingvaj.compiler.tree;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Contains lookups for classes
 */
public class CompilerIndex {
    private final ArrayList<CompilerClass> classes;
    private final HashMap<String, CompilerClass> classIndex;
    private final HashMap<MethodOrFieldIdentifier, CompilerMethod> methodIndex = new HashMap<>();
    private final HashMap<MethodOrFieldIdentifier, CompilerField> fieldIndex = new HashMap<>();

    public CompilerIndex(List<ClassNode> classNodes) {
        this.classes = new ArrayList<>(classNodes.size());

        // Create compiler classes from the ClassNodes
        for (ClassNode classNode : classNodes) {
            this.classes.add(new CompilerClass(classNode, false));
        }

        this.classIndex = new HashMap<>(this.classes.size());

        // Put the compiler classes in the map
        for (CompilerClass cc : this.classes) {
            refreshClass(cc);
        }
    }

    public void addGeneratedClass(ClassNode classNode) {
        var cc = new CompilerClass(classNode, false, true);

        this.classes.add(cc);

        this.refreshClass(cc);
    }

    public void refreshClass(CompilerClass cc) {
        this.classIndex.put(cc.getName(), cc);

        // Insert methods
        for (CompilerMethod method : cc.getMethods()) {
            this.methodIndex.put(method.getIdentifier(), method);
        }

        // Insert fields
        for (CompilerField method : cc.getFields()) {
            this.fieldIndex.put(method.getIdentifier(), method);
        }
    }

    public CompilerField getField(String owner, String name, String desc) {
        return this.fieldIndex.get(new MethodOrFieldIdentifier(owner, name, desc));
    }

    public CompilerField getField(FieldInsnNode fieldInsnNode) {
        return this.fieldIndex.get(new MethodOrFieldIdentifier(fieldInsnNode.owner,
                fieldInsnNode.name,
                fieldInsnNode.desc));
    }

    public CompilerMethod getMethod(MethodInsnNode methodInsn) {
        return getMethod(methodInsn.owner, methodInsn.name, methodInsn.desc);
    }

    public CompilerMethod getMethod(String owner, String name, String desc) {
        return this.methodIndex.get(new MethodOrFieldIdentifier(owner, name, desc));
    }

    public ArrayList<CompilerClass> getClasses() {
        return classes;
    }
}
