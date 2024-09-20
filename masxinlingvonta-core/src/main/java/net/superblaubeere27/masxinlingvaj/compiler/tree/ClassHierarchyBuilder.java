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

        buildVTree(index);
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


        return new ClassRelations(parent, parents, new ArrayList<>());
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

    private static void buildVTree(CompilerIndex ci) {
        var classQueue = new ArrayDeque<CompilerClass>();

        var objectClass = Objects.requireNonNull(ci.getClass("java/lang/Object"), "No Object class found");

        objectClass.setvTable(ClassVTable.create(null, objectClass));

        classQueue.add(objectClass);

        while (!classQueue.isEmpty()) {
            var parentClass = classQueue.removeFirst();

            for (var subClass : parentClass.getRelations().getSubClasses()) {
                if (!subClass.isInterface()) {
                    subClass.setvTable(ClassVTable.create(parentClass.getvTable(), subClass));

                    classQueue.add(subClass);
                }
            }
        }

        processInterfaces(ci, objectClass);
    }

    private static void processInterfaces(CompilerIndex index, CompilerClass objectClass) {
        // Contains the max depth of each interface in the tree.
        var interfaceDepthMap = new HashMap<String, Integer>();

        findInterfaceDepths(objectClass, interfaceDepthMap, 0);

        var entries = new ArrayList<>(interfaceDepthMap.entrySet());

        entries.sort(Comparator.comparingInt(x -> -x.getValue()));

        for (var entry : entries) {
            processInterface(index.getClass(entry.getKey()), null);
        }

        for (var entry : entries) {
            var cc = index.getClass(entry.getKey());
            var interfaceVTable = cc.getvTable();

            for (CompilerClass subClass : cc.getRelations().getSubClasses()) {
                if (!subClass.isInterface()) {
                    addInterfaceVTableToClassRecursively(subClass, interfaceVTable);
                }
            }
        }
    }

    private static void findInterfaceDepths(CompilerClass cc, HashMap<String, Integer> foundDepths, int depth) {
        if (cc.isInterface()) {
            foundDepths.put(cc.getName(), Math.max(depth, foundDepths.getOrDefault(cc.getName(), 0)));

        }
        if (cc.isInterface() || depth == 0) {
            for (CompilerClass subClass : cc.getRelations().getSubClasses()) {
                findInterfaceDepths(subClass, foundDepths, depth + 1);
            }
        }
    }

    private static void processInterface(CompilerClass subClass, ClassVTable parentVTable) {
        if (subClass.getvTable() == null) {
            subClass.setvTable(ClassVTable.create(parentVTable, subClass));
        }

        if (parentVTable != null) {
            subClass.getvTable().addMethodsFromInterface(parentVTable);
        }

        for (CompilerClass aClass : subClass.getRelations().getSubClasses()) {
            if (aClass.isInterface()) {
                processInterface(aClass, subClass.getvTable());
            }
        }
    }

    private static void addInterfaceVTableToClassRecursively(CompilerClass compilerClass, ClassVTable vTable) {
        var targetVtable = compilerClass.getvTable();

        targetVtable.addMethodsFromInterface(vTable);

        for (CompilerClass subClass : compilerClass.getRelations().getSubClasses()) {
            addInterfaceVTableToClassRecursively(subClass, vTable);
        }
    }

    public static List<CompilerMethod> getPossibleImplementationsCached(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        return POSSIBLE_IMPLEMENTATION_CASH.computeIfAbsent(new MethodOrFieldIdentifier(compilerClass.getName(), name.getName(), name.getDesc()), x -> getPossibleImplementations(index, compilerClass, name));
    }

    /**
     * Determines which methods could be called if a method of a given object is virtually invoked
     */
    public static List<CompilerMethod> getPossibleImplementations(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        var list = new ArrayList<CompilerMethod>();

        var entry = compilerClass.getvTable().getEntry(name);

        if (entry != null) {
            var classImplementation = entry.currentImplementation();

            if (classImplementation != null) {
                list.add(classImplementation);
            }
        } else if (!compilerClass.isInterface()) {
            // Concerning... This may happen in some edge cases, but it completely invalidates our results.
            return Collections.emptyList();
        }

        var error = getPossibleImplementationsInSubclasses(compilerClass, name, entry == null ? null : entry.currentImplementation(), list);

        if (error) {
            return Collections.emptyList();
        }

        list.trimToSize();

        return list;
    }

    private static boolean getPossibleImplementationsInSubclasses(CompilerClass compilerClass, MethodOrFieldName name, CompilerMethod superClassImplementation, ArrayList<CompilerMethod> results) {
        for (CompilerClass subClass : compilerClass.getRelations().getSubClasses()) {
            var entry = subClass.getvTable().getEntry(name);

            if (entry != null) {
                var implementation = entry.currentImplementation();

                if (implementation != null && !implementation.equals(superClassImplementation)) {
                    results.add(implementation);
                }
            } else if (!subClass.isInterface()) {
                throw new IllegalStateException("Method " + name + " not found vtable of in " + subClass.getName());
            }

            if (getPossibleImplementationsInSubclasses(subClass, name, superClassImplementation, results)) {
                return true;
            }
        }

        return false;
    }

    public static List<CompilerMethod> getImplementationsInSubClasses(CompilerIndex index, CompilerClass cc, MethodOrFieldName name) {
        var list = new ArrayList<CompilerMethod>();

        getImplementationsInSubClasses0(index, cc, name, list);

        return list;
    }

    private static void getImplementationsInSubClasses0(CompilerIndex index, CompilerClass cc, MethodOrFieldName name, ArrayList<CompilerMethod> list) {
        var method = index.getMethod(cc.getName(), name.getName(), name.getDesc());

        if (method != null) {
            list.add(method);
        }

        for (CompilerClass subClass : cc.getRelations().getSubClasses()) {
            getImplementationsInSubClasses0(index, subClass, name, list);
        }
    }

    /**
     * Determines which implementation shall be called by a virtual call for a given class.
     */
    public static CompilerMethod getVirtualImplementation(CompilerIndex index, CompilerClass compilerClass, MethodOrFieldName name) {
        var entry = compilerClass.getvTable().getEntry(name);

        return entry != null ? entry.currentImplementation() : null;
    }

}
