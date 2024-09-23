package net.superblaubeere27.masxinlingvonta.test.framework;

import java.lang.reflect.Method;

public class TestExecutor {

    public static void main(String[] args) {
        if (!args[0].equals("null")) {
            System.loadLibrary(args[0]);
        }

        boolean failureFlag = false;

        for (int i = 1; i < args.length; i++) {
            if (!runTest(args[i])) {
                System.out.println("Test FAILURE: " + args[i]);

                failureFlag = true;
            } else if (!runCfgTest(args[i])) {
                System.out.println("CFG test FAILURE: " + args[i]);

                failureFlag = true;
            } else {
                System.out.println("Test success: " + args[i]);
            }
        }

        System.exit(failureFlag ? 1 : 0);
    }

    private static boolean runTest(String name) {
        try {
            Class<?> clazz = Class.forName(name);

            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);

            return true;
        } catch (Throwable e) {
            e.printStackTrace();

            return false;
        }
    }

    private static boolean runCfgTest(String name) {
        try {
            Class<?> clazz = Class.forName(name);

            Method method = clazz.getMethod("verifyCFGText", String[].class);

            method.invoke(null, (Object) new String[0]);

            return true;
        } catch (NoSuchMethodException e) {
            return true;
        } catch (Throwable e) {
            e.printStackTrace();

            return false;
        }
    }
}
