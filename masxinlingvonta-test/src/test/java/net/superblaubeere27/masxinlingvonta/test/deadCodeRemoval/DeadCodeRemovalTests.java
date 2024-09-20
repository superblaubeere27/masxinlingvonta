package net.superblaubeere27.masxinlingvonta.test.deadCodeRemoval;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class DeadCodeRemovalTests {
//    static Object obj = new Object();
//
//    @Outsource
//    public static boolean test() {
//        if (obj.getClass() == Object.class) {
//            synchronized (obj) {
//                return true;
//            }
//        }
//        return false;
//    }

    @Outsource
    private static int test(int rhs) {
        return Integer.valueOf(rhs).intValue();
    }

    public static void main(String[] args) {
    }
}
