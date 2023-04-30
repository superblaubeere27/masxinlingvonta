package net.superblaubeere27.masxinlingvonta.test;

public class TestMain {

    public static void main(String[] args) {
        System.loadLibrary("test-native");

        BranchTest.test();
    }

    private static class A {
        protected void doShit() {

        }
    }

    private static abstract class B extends A {
        protected abstract void doShit();
    }

}
