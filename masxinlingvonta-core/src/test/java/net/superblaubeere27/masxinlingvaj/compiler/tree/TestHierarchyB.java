package net.superblaubeere27.masxinlingvaj.compiler.tree;

/**
 * Tests this kind of tree:
 * O
 * /\
 * | I1*
 * | /\
 * C1  I2*
 * \/
 * C2
 * = Interface with an implementation of a method m()
 * Implementations deeper into the tree are prioritized over implementations higher in the tree.
 * In this case C1.m() should be implemented by I1 and C2.m() should be implemented by I2.
 */
class TestHierarchyB {
    static final Class<?>[] CLASSES = {
            SuperInterface.class,
            Interface.class,
            SuperClass.class,
            SubClass.class
    };

    interface SuperInterface {
        default void defaultMethod() {
        }
    }

    interface Interface extends SuperInterface {
        @Override
        default void defaultMethod() {

        }
    }

    static class SuperClass implements SuperInterface {
    }

    static class SubClass extends SuperClass implements Interface {
    }

}
