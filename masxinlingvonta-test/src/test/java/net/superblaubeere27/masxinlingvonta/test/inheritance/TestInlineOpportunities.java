package net.superblaubeere27.masxinlingvonta.test.inheritance;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class TestInlineOpportunities {

    public static void main(String[] args) {
        if (testB(new S()) != 3 * 2) {
            throw new RuntimeException("Test failed S");
        }
        if (testB(new D()) != 3 * 17) {
            throw new RuntimeException("Test failed D");
        }
        if (testB(new A()) != 5 * 7) {
            throw new RuntimeException("Test failed A");
        }
        if (testB(new B()) != 13 * 17) {
            throw new RuntimeException("Test failed B");
        }
    }

    public static void verifyCFGText() {

    }

    @Outsource
    private static int testB(S s) {
        if (s == null) {
            return -1;
        }

        if (!(s instanceof D)) {
            return s.test() * 2;
        }

        if (s instanceof A) {
            return s.test() * 7;
        }

        // This may not be inlined since s.test might be S.test or B.test
        return s.test() * 17;
    }

    private static class S {
        public int test() {
            return 3;
        }
    }

    private static class D extends S {
    }

    private static class A extends D {
        public int test() {
            return 5;
        }
    }

    private static class B extends D {
        public int test() {
            return 13;
        }
    }

}
