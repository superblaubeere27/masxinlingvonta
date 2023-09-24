package net.superblaubeere27.masxinlingvonta.test.compilerTests.lambda_codegen;

import java.util.function.Consumer;
import java.util.function.Function;

public class TestLambdaCodegen {

    private static int callBitMask = 0;

    public static void runTests() {
        callBitMask = 0;

        testInvokeStaticWithoutArgs();

        assert callBitMask == 0b111;

        callBitMask = 0;

        testInvokeVirtual();

        assert callBitMask == 0b111;

        testStackCapture();

        assert callBitMask == 0b1;
    }

    private static void testInvokeStaticWithoutArgs() {
        Runnable a = TestLambdaCodegen::invokeStaticWithoutArgs;

        a.run();

        Consumer<String> b = TestLambdaCodegen::invokeStaticWithArgs;

        b.accept("ab");

        AllArgsInterface c = TestLambdaCodegen::invokeStaticWithAllArgs;

        c.invoke((byte) 1, (short) -((short) 'a'), 'a', -1, -10, 10.0f, 4.0);
    }

    private static void testInvokeVirtual() {
        Function<Integer, InvokeThingy> newFun = InvokeThingy::new;

        InvokeThingy instance = newFun.apply(2);

        // Capture stack
        Runnable callDoStuff = instance::doStuff;

        callDoStuff.run();

        Consumer<InvokeThingy> c = InvokeThingy::doStuff;

        c.accept(new InvokeThingy(4, 0));
    }

    private static void testStackCapture() {
        int a = 1;
        long b = -1;
        short c = -2;
        String d = "bb";
        double f = 1.0;

        Runnable run = () -> {
            callBitMask |= a + (int) b + c + d.length() + (int) f;
        };

        run.run();
    }

    private static void invokeStaticWithoutArgs() {
        callBitMask |= 1;
    }

    private static void invokeStaticWithArgs(String a) {
        callBitMask |= a.length();
    }

    private static void invokeStaticWithAllArgs(byte a, short b, char c, int d, long e, float f, double g) {
        callBitMask |= a + b + c + d + e + (int) f + (int) g;
    }

    interface AllArgsInterface {
        void invoke(byte a, short b, char c, int d, long e, float f, double g);
    }

    private static class InvokeThingy {
        private final int index;

        public InvokeThingy(int index) {
            callBitMask |= index >> 1;

            this.index = index;
        }

        public InvokeThingy(int index, int i1) {
            this.index = index;
        }

        public void doStuff() {
            callBitMask |= index;
        }
    }

}
