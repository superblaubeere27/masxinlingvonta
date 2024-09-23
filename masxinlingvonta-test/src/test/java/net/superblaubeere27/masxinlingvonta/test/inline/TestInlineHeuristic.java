package net.superblaubeere27.masxinlingvonta.test.inline;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class TestInlineHeuristic {

    public static void main(String[] args) {
        System.out.println(test("Hello, ", "World!"));
    }

    //    @Outsource
//    private static ArrayList<String> test(int i) {
//        ArrayList<String> arr = new ArrayList<>();
//
//        for (int num = 0; num < i; num++) {
//            arr.add(Integer.toString(num));
//        }
//
//        return arr;
//    }
    @Outsource
    private static String test(String a, String b) {
        return a + b;
    }

}
