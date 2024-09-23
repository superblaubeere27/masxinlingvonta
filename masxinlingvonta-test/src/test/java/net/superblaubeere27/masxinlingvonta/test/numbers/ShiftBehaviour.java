package net.superblaubeere27.masxinlingvonta.test.numbers;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class ShiftBehaviour {

    @Outsource
    private static int ishrN(int a, int b) {
        return a >> -b;
    }

    @Outsource
    private static int iushrN(int a, int b) {
        return a >>> -b;
    }

    @Outsource
    private static int ishlN(int a, int b) {
        return a << -3;
    }

    private static int ishrJ(int a, int b) {
        return a >> -b;
    }

    private static int iushrJ(int a, int b) {
        return a >>> -b;
    }

    private static int ishlJ(int a, int b) {
        return a << -b;
    }

    public static void main(String[] args) {
        testNumbers(4096, 3);
        testNumbers(4096, 31);
        testNumbers(4096, 32);
        testNumbers(4096, -31);
        testNumbers(4096, -3);
        testNumbers(4096, 0);
    }

    private static void testNumbers(int a, int b) {
        assertTrue(ishrN(a, b) == ishrJ(a, b), a + " shr " + b);
        assertTrue(ishlN(a, b) == ishlJ(a, b), a + " shl " + b);
        assertTrue(iushrN(a, b) == iushrJ(a, b), a + " ushr " + b);
    }

    private static void assertTrue(boolean b, String s) {
        if (!b) throw new IllegalStateException(s);
    }

}
