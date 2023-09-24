package net.superblaubeere27.masxinlingvonta.test.compilerTests.passes.reuselocal;

import java.util.Random;

class Test {
    private static final Random RANDOM = new Random();
    private int i;

    private Test(int i) {
        this.i = i;
    }

    private int testA() {
        int b = this.i;

        if (b > 5) {
            fence();
        }

        return this.i;
    }

    private int testB() {
        int b = this.i;

        if (b > 5) {
            fence();
        } else {
            return this.i;
        }

        return 5;
    }

    private int testC() {
        fence();

        int b = this.i;

        if (b > 5) {
            fence();
        } else {
            int a = this.i;

            fence();

            return a + b;
        }

        return 5;
    }

    private int testD() {
        fence();

        int b = this.i;

        try {
            b = this.i;
        } catch (Exception e) {
            fence();
        }

        if (b > 5) {
            fence();
        } else {
            int a = this.i;

            fence();

            return a + b;
        }

        return 5;
    }

    private int testE() {
        int a = this.i;
        int b = this.i;

        fence();

        int c = this.i;

        return a * c + b;
    }

    private int testF() {
        int a = this.i;
        int b = this.i;

        this.i = RANDOM.nextInt();

        int c = this.i;

        return a * c + b;
    }

    private void fence() {
        this.i = RANDOM.nextInt();
    }

}
