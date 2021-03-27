package net.superblaubeere27.masxinlingvonta.test;

public class Assertions {

    public static void assertTrue(boolean flag) {
        if (!flag)
            fail();
    }


    public static void fail() {
        throw new AssertionError();
    }

    public static boolean supplyTrue() {
        return true;
    }

    public static boolean supplyFalse() {
        return false;
    }

}
