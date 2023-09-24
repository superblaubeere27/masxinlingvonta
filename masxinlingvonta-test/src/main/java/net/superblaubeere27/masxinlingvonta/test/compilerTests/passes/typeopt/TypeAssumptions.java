package net.superblaubeere27.masxinlingvonta.test.compilerTests.passes.typeopt;

public class TypeAssumptions {

    public static boolean testEqStuff(String a, Object b) {
        Object c;

        if (a == null) {
            c = a;
        } else {
            c = Boolean.FALSE;
        }

        return c.equals(b);
    }

}
