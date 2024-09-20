package net.superblaubeere27.masxinlingvonta.test.deadCodeRemoval;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

import java.util.function.Consumer;

public class TestDeadArrayCodeRemoval {

    public static void main(String[] args) {

    }

    @Outsource
    private static void testA(Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return;

        Object[] arr = new Object[2];

        arr[0] = Boolean.FALSE;
    }

    @Outsource
    private static int testLenSameBlock(Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return 0;

        Object[] arr = new Object[2];

        arr[0] = Boolean.FALSE;

        return arr.length;
    }

    @Outsource
    private static int testLenOtherBlock(int i, Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return 0;
        Object[] arr = new Object[2];

        arr[0] = Boolean.FALSE;

        if (i > 0) {
            return arr.length;
        } else {
            return 0;
        }
    }

    @Outsource
    private static void testBlackboxSameBlock(Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return;
        Object[] arr = new Object[2];

        arr[0] = Boolean.FALSE;

        blackbox.accept(arr);
    }

    @Outsource
    private static void testBeforeSameBlackboxSameBlock(Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return;
        Object[] arr = new Object[2];

        blackbox.accept(arr);

        arr[0] = Boolean.FALSE;

    }

    @Outsource
    private static void testBeforeSameBlackboxSameBlock(int i, Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return;
        Object[] arr = new Object[2];

        if (i > 0) {
            blackbox.accept(arr);
        }

        arr[0] = Boolean.FALSE;
    }

    @Outsource
    private static void testAfterBlackboxOtherBlock(int i, Consumer<Object[]> blackbox) {
        if (blackbox == null)
            return;
        Object[] arr = new Object[2];

        arr[0] = Boolean.FALSE;

        if (i > 0) {
            blackbox.accept(arr);
        }

    }
}
