package net.superblaubeere27.masxinlingvonta.test.inheritance;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;
import net.superblaubeere27.masxinlingvonta.test.framework.TestMain;
import net.superblaubeere27.masxinlingvonta.test.framework.TestMilestone;

import static net.superblaubeere27.masxinlingvonta.test.framework.MLVTestFramework.milestoneReached;

public class TestAbstractMethodOverride {
    private static final String CORRECT_DEFAULT_CALL = "correctDefaultCall";
    private static final String CORRECT_SUPER_CLASS_CALL = "correctSuperClassCall";
    private static final String CORRECT_OVERRIDE_CALL = "correctOverrideCall";
    private static final String CORRECT_INTERFACE_CALL = "correctInterfaceCall";

    private static final int N_PARENT_RETURN_VALUE = 1;
    private static final int N_ABSTRACT_OVERRIDE_RETURN_VALUE = 3;
    private static final int N_INTERFACE_OVERRIDE_RETURN_VALUE = 3;

    private static final int N_INTERFACE_RETURN_VALUE = 7;

    @TestMain(milestones = {
            @TestMilestone(CORRECT_DEFAULT_CALL),
            @TestMilestone(CORRECT_SUPER_CLASS_CALL),
            @TestMilestone(CORRECT_OVERRIDE_CALL),
            @TestMilestone(CORRECT_INTERFACE_CALL)
    })
    public static void main(String[] args) {
        var nonOverriding = new NonOverridingImplementation();
        var superClass = new SuperClass();
        var overriding = new Overriding();
        var implementationOfParentInterface = new ImplementationOfParentInterface();

        if (testA(nonOverriding) != testWithInterface(nonOverriding)) {
            throw new Error("result of testA was not equal to result of testWithInterface");
        }
        if (testA(superClass) != testWithInterface(superClass)) {
            throw new Error("result of testA was not equal to result of testWithInterface");
        }
        if (testA(overriding) != testWithInterface(overriding)) {
            throw new Error("result of testA was not equal to result of testWithInterface");
        }

        if (testA(nonOverriding) != N_PARENT_RETURN_VALUE) {
            throw new Error("wrong implementation called");
        } else {
            milestoneReached(CORRECT_DEFAULT_CALL);
        }

        if (testA(superClass) != N_PARENT_RETURN_VALUE) {
            throw new Error("wrong implementation called");
        } else {
            milestoneReached(CORRECT_SUPER_CLASS_CALL);
        }

        if (testA(overriding) != N_ABSTRACT_OVERRIDE_RETURN_VALUE) {
            throw new Error("wrong implementation called");
        } else {
            milestoneReached(CORRECT_OVERRIDE_CALL);
        }
        if (testWithInterface(implementationOfParentInterface) != N_INTERFACE_OVERRIDE_RETURN_VALUE) {
            throw new Error("wrong implementation called");
        } else {
            milestoneReached(CORRECT_OVERRIDE_CALL);
        }
    }

    @Outsource
    private static int testA(SuperClass obj) {
        if (obj == null) {
            return -1;
        }

        return obj.n();
    }

    @Outsource
    private static int testWithInterface(ParentInterface obj) {
        if (obj == null) {
            return -1;
        }

        return obj.n();
    }

    interface ParentInterface {
        default int n() {
            return N_INTERFACE_RETURN_VALUE;
        }
    }

    private static class SuperSuperClass {
        public int n() {
            return N_PARENT_RETURN_VALUE;
        }
    }

    private static class ImplementationOfParentInterface implements ParentInterface {
        @Override
        public int n() {
            return N_INTERFACE_OVERRIDE_RETURN_VALUE;
        }
    }

    private static class SuperClass extends SuperSuperClass implements ParentInterface {
    }

    static class NonOverridingImplementation extends SuperClass {
    }

    static class Overriding extends SuperClass {
        public int n() {
            return N_ABSTRACT_OVERRIDE_RETURN_VALUE;
        }
    }
}
