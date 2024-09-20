package net.superblaubeere27.masxinlingvonta.test.inline;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

import java.util.ArrayList;

public class TestInlineHeuristic {

    public static void main(String[] args) {

    }

    @Outsource
    private static int testB(int w, int h) {
        ArrayList<Object> arr = new ArrayList<>();

        arr.add(null);

        return arr.size();
    }

}
