package net.superblaubeere27.masxinlingvonta.test.synchronization;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class StaticSynchronizedTest {
    private static final boolean didShit = false;
    private static int i = 0;

    private static synchronized void waitForStuff() throws InterruptedException {
        Thread.sleep(300);

        i = 20;
    }

    @Outsource
    private static void waitForStuffA() throws InterruptedException {
        waitForStuff();
    }

    @Outsource
    private static synchronized void waitLonger() {
        if (i != 20) {
            throw new IllegalStateException();
        }
    }

    @Outsource
    private static void waitLongerA() throws InterruptedException {
        waitLonger();
    }

    public static void main(String[] args) throws InterruptedException {
        new Thread(() -> {
            try {
                StaticSynchronizedTest.waitForStuffA();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(50);

        StaticSynchronizedTest.waitLongerA();
    }

}
