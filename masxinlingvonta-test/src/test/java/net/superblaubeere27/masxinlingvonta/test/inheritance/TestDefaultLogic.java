package net.superblaubeere27.masxinlingvonta.test.inheritance;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;
import net.superblaubeere27.masxinlingvonta.test.framework.TestMain;
import net.superblaubeere27.masxinlingvonta.test.framework.TestMilestone;

import static net.superblaubeere27.masxinlingvonta.test.framework.MLVTestFramework.milestoneReached;

public class TestDefaultLogic {
    private static final String CORRECT_DEFAULT_CALL = "correctDefaultCall";
    private static final String CORRECT_SUPER_CLASS_CALL = "correctSuperClassCall";
    private static final String CORRECT_OVERRIDE_CALL = "correctDefaultCall";

    private static final int N_DEFAULT_RETURN_VALUE = 1;
    private static final int M_DEFAULT_RETURN_VALUE = 2;
    private static final int N_OVERRIDE_RETURN_VALUE = 3;
    private static final int M_OVERRIDE_RETURN_VALUE = 5;

    private static final int SUPER_INTERFACE_RETURN_VALUE = 7;
    private static final int INTERFACE_RETURN_VALUE = 13;

    @TestMain(milestones = {
            @TestMilestone(CORRECT_DEFAULT_CALL),
            @TestMilestone(CORRECT_SUPER_CLASS_CALL),
            @TestMilestone(CORRECT_OVERRIDE_CALL)
    })
    public static void main(String[] args) {
        int idC = test(new NonOverridingImplementation());
        if (idC != N_DEFAULT_RETURN_VALUE * M_DEFAULT_RETURN_VALUE) {
            throw new Error("C.m didn't invoke I.m: id " + idC);
        } else {
            milestoneReached(CORRECT_DEFAULT_CALL);
        }

        int idA = test(new SuperClass());
        if (idA != N_DEFAULT_RETURN_VALUE * M_DEFAULT_RETURN_VALUE) {
            throw new Error("C.m didn't invoke I.m: id " + idA);
        } else {
            milestoneReached(CORRECT_SUPER_CLASS_CALL);
        }

        int idD = test(new Overriding());
        if (idD != N_OVERRIDE_RETURN_VALUE * M_OVERRIDE_RETURN_VALUE) {
            throw new Error("D.m didn't invoke D.m: id " + idD);
        } else {
            milestoneReached(CORRECT_OVERRIDE_CALL);
        }
        int idE = testB(new OtherSuperClass());

        if (idE != SUPER_INTERFACE_RETURN_VALUE) {
            throw new Error("Invalid method invoked A: " + idE);
        }
        int idF = testB(new OtherSubClass());

        if (idF != INTERFACE_RETURN_VALUE) {
            throw new Error("Invalid method invoked B: " + idF);
        }
    }

    @Outsource
    public static int test(SuperClass obj) {
        if (obj == null) {
            return -1;
        }

        // Problems occurred when the optimizer figured that the method implmenetation of the Overriding class is the
        // only possible implementation of this class
        return obj.m() * obj.n();
    }

    @Outsource
    public static int testB(OtherSuperClass obj) {
        if (obj == null) {
            return -1;
        }

        return obj.defaultMethod();
    }

    interface ParentInterfaceWithAnotherDefaultImpl {
        default int n() {
            return N_DEFAULT_RETURN_VALUE;
        }
    }

    interface InterfaceWithDefaultImpl extends ParentInterfaceWithAnotherDefaultImpl {
        default int m() {
            return M_DEFAULT_RETURN_VALUE;
        }
    }

    interface SuperInterface {
        default int defaultMethod() {
            return SUPER_INTERFACE_RETURN_VALUE;
        }
    }

    interface Interface extends SuperInterface {
        @Override
        default int defaultMethod() {
            return INTERFACE_RETURN_VALUE;
        }
    }

    static class SuperClass implements InterfaceWithDefaultImpl {
    }

    static class NonOverridingImplementation extends SuperClass {
    }

    static class Overriding extends SuperClass {
        public int m() {
            return M_OVERRIDE_RETURN_VALUE;
        }

        public int n() {
            return N_OVERRIDE_RETURN_VALUE;
        }
    }

    static class OtherSuperClass implements SuperInterface {
    }

    static class OtherSubClass extends OtherSuperClass implements Interface {
    }
}
