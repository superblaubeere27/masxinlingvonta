package net.superblaubeere27.masxinlingvonta.test.regressions;

public class RegAssumptionTest0 {

    public static void main(String[] args) {
        assert testA(false, new A(1337)) == 1338;
        assert testA(true, new A(1337)) == 1337;
        assert testA(true, new A(4)) == 4;
        assert testA(false, new A(4)) == 4;
    }

    private static int testA(boolean c, A a) {
        int b = a.a;

        if (a.a > 5) {
            b += 1;
        }

        return c ? a.a : b;
    }

    private static class A {
        private final int a;

        private A(int a) {
            this.a = a;
        }
    }

}
