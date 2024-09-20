package net.superblaubeere27.masxinlingvaj.compiler.tree;

class TestHierarchyA {
    static final Class<?>[] CLASSES = {
            SuperClass.class,
            InterfaceSuper.class,
            Interface.class,
            ImplementsInterface.class,
            OnlyOverridesSuperClass.class,
            SubClass.class,
            SubSubClass.class,
            SubSubSubClass.class
    };

    interface InterfaceSuper {
        void sharedWithInterface();
    }

    interface Interface extends InterfaceSuper {
        default void interfaceDefaultMethod() {

        }
    }

    static class SuperClass {
        public void sharedWithInterface() {

        }

        public void interfaceDefaultMethod() {

        }
    }

    static class SubClass extends SuperClass {

    }

    static class SubSubClass extends SubClass implements Interface {
    }

    static class SubSubSubClass extends SubSubClass {
        @Override
        public void sharedWithInterface() {

        }

        @Override
        public void interfaceDefaultMethod() {

        }
    }

    class ImplementsInterface implements Interface {

        @Override
        public void sharedWithInterface() {

        }
    }

    class OnlyOverridesSuperClass extends SuperClass {
        @Override
        public void sharedWithInterface() {

        }

        @Override
        public void interfaceDefaultMethod() {

        }
    }

}
