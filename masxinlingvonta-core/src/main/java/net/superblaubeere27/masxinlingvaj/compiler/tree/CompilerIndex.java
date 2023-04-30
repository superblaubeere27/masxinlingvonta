package net.superblaubeere27.masxinlingvaj.compiler.tree;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains lookups for classes
 */
public class CompilerIndex {
    private final ArrayList<CompilerClass> classes;
    private final HashMap<String, CompilerClass> classIndex;
    private final HashMap<MethodOrFieldIdentifier, CompilerMethod> methodIndex = new HashMap<>();
    private final HashMap<MethodOrFieldIdentifier, CompilerField> fieldIndex = new HashMap<>();

    public CompilerIndex(List<ClassNode> classNodes, ArrayList<ClassNode> libraryClasses) {
        this.classes = new ArrayList<>(classNodes.size() + libraryClasses.size());

        // Create compiler classes from the ClassNodes
        for (ClassNode classNode : classNodes) {
            this.classes.add(new CompilerClass(classNode, false));
        }
        for (ClassNode classNode : libraryClasses) {
            this.classes.add(new CompilerClass(classNode, true));
        }

        this.classIndex = new HashMap<>(this.classes.size());

        // Put the compiler classes in the map
        for (CompilerClass cc : this.classes) {
            refreshClass(cc);
        }
    }

    public void addGeneratedClasses(ArrayList<ClassNode> classNode) {
        var classes = classNode.stream().map(cn -> new CompilerClass(cn, false, true)).collect(Collectors.toList());

        this.classes.addAll(classes);

        classes.forEach(this::refreshClass);

        ClassHierarchyBuilder.addGeneratedClasses(this, classes);
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

    public void buildHierarchy() {
        ClassHierarchyBuilder.buildHierarchy(this);
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

    public CompilerMethod getMethod(MethodOrFieldIdentifier target) {
        return this.getMethod(target.getOwner(), target.getName(), target.getDesc());
    }

    public CompilerClass getClass(String name) {
        return this.classIndex.get(name);
    }

    public ArrayList<CompilerClass> getClasses() {
        return classes;
    }
}
