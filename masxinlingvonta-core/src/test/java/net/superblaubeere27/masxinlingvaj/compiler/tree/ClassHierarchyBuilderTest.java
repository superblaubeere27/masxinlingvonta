package net.superblaubeere27.masxinlingvaj.compiler.tree;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassHierarchyBuilderTest {
    private static CompilerIndex compilerIndex;

    @BeforeAll
    public static void loadClasses() throws IOException {
        ArrayList<ClassNode> loadedClasses = new ArrayList<>();

        var classesToLoad = new ArrayList<Class<?>>();

        classesToLoad.addAll(Arrays.asList(TestHierarchyA.CLASSES));
        classesToLoad.addAll(Arrays.asList(TestHierarchyB.CLASSES));

        classesToLoad.add(Object.class);

        for (var aClass : classesToLoad) {
            var cn = new ClassNode();
            new ClassReader(aClass.getResourceAsStream("/" + aClass.getName().replace('.', '/') + ".class").readAllBytes()).accept(cn, 0);

            loadedClasses.add(cn);
        }

        compilerIndex = new CompilerIndex(Collections.emptyList(), loadedClasses);

        ClassHierarchyBuilder.buildHierarchy(compilerIndex);
    }

    private static String getSystemName(Class<?> clazz) {
        return clazz.getName().replace('.', '/');
    }

    private static void assertContainsAll(List<CompilerMethod> methods, MethodLink... links) {
        assertEquals(links.length, methods.size());

        for (MethodLink link : links) {
            assertTrue(methods.stream().anyMatch(m -> m.getParent().getName().equals(getSystemName(link.clazz)) && m.getNode().name.equals(link.name)));
        }
    }

    @Test
    public void testHierarchyA() {
        var subClass = compilerIndex.getClass(getSystemName(TestHierarchyA.SubClass.class));
        var subSubSubclass = compilerIndex.getClass(getSystemName(TestHierarchyA.SubSubSubClass.class));
        var interfaceClass = compilerIndex.getClass(getSystemName(TestHierarchyA.Interface.class));

        var subClassHierarchy = ClassHierarchyBuilder.getPossibleImplementations(compilerIndex, subClass, new MethodOrFieldName("sharedWithInterface", "()V"));

        assertContainsAll(subClassHierarchy, new MethodLink(TestHierarchyA.SubSubSubClass.class, "sharedWithInterface"), new MethodLink(TestHierarchyA.SuperClass.class, "sharedWithInterface"));

        var subSubSubClassHierarchy = ClassHierarchyBuilder.getPossibleImplementations(compilerIndex, subSubSubclass, new MethodOrFieldName("sharedWithInterface", "()V"));

        assertContainsAll(subSubSubClassHierarchy, new MethodLink(TestHierarchyA.SubSubSubClass.class, "sharedWithInterface"));

        var interfaceSuperClassHierarchy = ClassHierarchyBuilder.getPossibleImplementations(compilerIndex, interfaceClass, new MethodOrFieldName("sharedWithInterface", "()V"));

        assertContainsAll(interfaceSuperClassHierarchy, new MethodLink(TestHierarchyA.SubSubSubClass.class, "sharedWithInterface"), new MethodLink(TestHierarchyA.SuperClass.class, "sharedWithInterface"), new MethodLink(TestHierarchyA.ImplementsInterface.class, "sharedWithInterface"));
        var interfaceSuperClassAktHierarchy = ClassHierarchyBuilder.getPossibleImplementations(compilerIndex, interfaceClass, new MethodOrFieldName("interfaceDefaultMethod", "()V"));

        assertContainsAll(interfaceSuperClassAktHierarchy, new MethodLink(TestHierarchyA.SubSubSubClass.class, "interfaceDefaultMethod"), new MethodLink(TestHierarchyA.SuperClass.class, "interfaceDefaultMethod"), new MethodLink(TestHierarchyA.Interface.class, "interfaceDefaultMethod"));
    }

    @Test
    public void testHierarchyB() {
        var superClass = compilerIndex.getClass(getSystemName(TestHierarchyB.SuperClass.class));
        var subClass = compilerIndex.getClass(getSystemName(TestHierarchyB.SubClass.class));

        var superClassImplementationParent = Objects.requireNonNull(superClass.getvTable().getEntry(new MethodOrFieldName("defaultMethod", "()V"))).currentImplementation();
        var subClassImplementationParent = Objects.requireNonNull(subClass.getvTable().getEntry(new MethodOrFieldName("defaultMethod", "()V"))).currentImplementation();

        assertEquals(getSystemName(TestHierarchyB.SuperInterface.class), superClassImplementationParent.getParent().getName());
        assertEquals(getSystemName(TestHierarchyB.Interface.class), subClassImplementationParent.getParent().getName());
    }

    private record MethodLink(Class<?> clazz, String name) {

    }

}