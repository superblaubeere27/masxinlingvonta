package net.superblaubeere27.masxinlingvonta.test;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

import static net.superblaubeere27.masxinlingvonta.test.Assertions.*;

public class BranchTest {

    public static void test() {
        testBooleanOperations();
        testIntOperations();
        testACMP();
    }

    @Outsource
    public static void testBooleanOperations() {
        if (supplyTrue()) {

        } else {
            fail();
        }

        if (!supplyTrue()) {
            fail();
        }

        if (supplyFalse())
            fail();

        if (!supplyFalse()) {

        } else {
            fail();
        }

        if (supplyFalse() || supplyTrue()) {

        } else fail();

        if (supplyTrue() || supplyTrue()) {

        } else fail();

        if (supplyTrue() || supplyFalse()) {

        } else fail();

        if (supplyTrue() && supplyFalse()) {
            fail();
        }
        if (supplyFalse() && supplyFalse()) {
            fail();
        }
        if (supplyTrue() && supplyTrue()) {

        } else fail();
    }

    @Outsource
    public static void testIntOperations() {
        if (getInt() > zero()) {

        } else fail();

        if (getInt() >= zero()) {

        } else fail();

        if (getInt() != zero()) {

        } else fail();

        if (getInt() < zero()) {
            fail();
        }

        if (getInt() <= zero()) {
            fail();
        }

        if (getInt() == zero()) {
            fail();
        }

        if (getInt() > getSmolInt()) {

        } else fail();

        if (getInt() >= getSmolInt()) {

        } else fail();

        if (getInt() != getSmolInt()) {

        } else fail();

        if (getInt() < getSmolInt()) {
            fail();
        }

        if (getInt() <= getSmolInt()) {
            fail();
        }

        if (getInt() == getSmolInt()) {
            fail();
        }
    }

    @Outsource
    public static void testACMP() {
        if (getNull() == null) {

        } else fail();

        if (getNull() != null) {
            fail();
        }

        if (getNonNull() != null) {

        } else fail();

        if (getNonNull() == null) {
            fail();
        }

        if (null == getNull()) {

        } else fail();

        if (null != getNull()) {
            fail();
        }

        if (null != getNonNull()) {

        } else fail();

        if (null == getNonNull()) {
            fail();
        }


        if (getNonNull() == getNonNull()) {

        } else fail();

        if (getNonNull() != getNonNull()) {
            fail();
        }

        if (getNonNull() != getNonNullB()) {

        } else fail();

        if (getNonNull() == getNonNullB()) {
            fail();
        }
    }

    private static Object getNull() {
        return null;
    }

    private static Object getNonNull() {
        return BranchTest.class;
    }

    private static Object getNonNullB() {
        return Object.class;
    }

    private static int getSmolInt() {
        return 3;
    }

    private static int zero() {
        return 0;
    }

    private static int getInt() {
        return 12;
    }

}
