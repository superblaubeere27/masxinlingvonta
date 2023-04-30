package net.superblaubeere27.masxinlingvaj.compiler.tree;

import java.util.*;

public class ClassHierarchyBuilder {

    private static final HashMap<MethodOrFieldIdentifier, List<CompilerMethod>> POSSIBLE_IMPLEMENTATION_CASH = new HashMap<>();

    static void buildHierarchy(CompilerIndex index) {
        // Link parent classes
        for (CompilerClass aClass : index.getClasses()) {
            ClassRelations relations = buildClassRelationsWithoutSuperClasses(index, aClass);

            aClass.setRelations(relations);
        }

        CompilerClass objectClass = null;

        // Add subclasses
        for (CompilerClass aClass : index.getClasses()) {
            // java/lang/Object does not have a parent class
            if (aClass.getRelations().getSuperClass() == null) {
                assert objectClass == null;

                objectClass = aClass;
                continue;
            }

            updateParentClasses(aClass);
        }

        assert objectClass != null && objectClass.getName().equals("java/lang/Object");
    }

    static void addGeneratedClasses(CompilerIndex index, List<CompilerClass> generatedClasses) {
        for (CompilerClass generatedClass : generatedClasses) {
            ClassRelations relations = buildClassRelationsWithoutSuperClasses(index, generatedClass);

            generatedClass.setRelations(relations);
        }

        for (CompilerClass generatedClass : generatedClasses) {
            updateParentClasses(generatedClass);
        }
    }

    private static void updateParentClasses(CompilerClass aClass) {
        for (CompilerClass parentClass : aClass.getRelations().getParentClasses()) {
            parentClass.getRelations().getSubClasses().add(aClass);
        }
    }

    private static ClassRelations buildClassRelationsWithoutSuperClasses(CompilerIndex index, CompilerClass aClass) {
        var superName = aClass.getClassNode().superName;
        var parents = new ArrayList<CompilerClass>();

        CompilerClass parent;

        if (superName != null) {
            parent = index.getClass(superName);

            if (parent == null) {
                throw new IllegalStateException("Missing super class " + superName + " of " + aClass.getName());
            }

            parents.add(parent);
        } else {
            if (!aClass.getName().equals("java/lang/Object")) {
                throw new IllegalStateException();
            }

            parent = null;
        }

        for (String interfaceName : aClass.getClassNode().interfaces) {
            var itf = index.getClass(interfaceName);

            if (itf == null) {
                throw new IllegalStateException("Missing parent class " + interfaceName + " of " + aClass.getName());
            }

            parents.add(itf);
        }


        ClassRelations relations = new ClassRelations(parent, parents, new ArrayList<>());
        return relations;
    }

    public static boolean isInstanceOf(CompilerClass clazz, CompilerClass of) {
        if (clazz.getName().equals(of.getName()))
            return true;

        for (CompilerClass parent : clazz.getRelations().getParentClasses()) {
            if (isInstanceOf(parent, of))
                return true;
        }

        return false;
    }

    public static List<CompilerMethod> getPossibleImplementationsCached(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        return POSSIBLE_IMPLEMENTATION_CASH.computeIfAbsent(new MethodOrFieldIdentifier(compilerClass.getName(), name.getName(), name.getDesc()), x -> getPossibleImplementations(index, compilerClass, name));
    }

    /**
     * Determines which methods could be called if a method of a given object is virtually invoked
     */
    public static List<CompilerMethod> getPossibleImplementations(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        var list = new ArrayList<CompilerMethod>();

        getPossibleImplementations0(index, compilerClass, name, list);

        list.trimToSize();

        return list;
    }

    private static void getPossibleImplementations0(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name, ArrayList<CompilerMethod> results) {
        CompilerMethod method = index.getMethod(compilerClass.getName(), name.getName(), name.getDesc());

        if (method != null) {
            if (method.isStatic())
                throw new IllegalStateException("A virtual method cannot be static");

            if (!method.isAbstract())
                results.add(method);
        }

        for (CompilerClass subClass : compilerClass.getRelations().getSubClasses()) {
            getPossibleImplementations0(index, subClass, name, results);
        }
    }

    /**
     * Determines which implementation shall be called by a virtual call for a given class.
     */
    public static CompilerMethod getVirtualImplementation(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        var method = index.getMethod(compilerClass.getName(), name.getName(), name.getDesc());

        if (method != null) {
            if (method.isStatic())
                throw new IllegalStateException("A virtual method cannot be static");

            if (!method.isAbstract())
                return method;
        }

        for (CompilerClass parentClass : compilerClass.getRelations().getParentClasses()) {
            var impl = getVirtualImplementation(index, parentClass, name);

            if (impl != null)
                return impl;
        }

        return null;
    }

}
