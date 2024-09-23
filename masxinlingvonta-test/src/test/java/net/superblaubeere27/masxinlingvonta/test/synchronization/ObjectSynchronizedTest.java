package net.superblaubeere27.masxinlingvonta.test.synchronization;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class ObjectSynchronizedTest {
    private final boolean didShit = false;
    private int i = 0;

    public static void main(String[] args) throws InterruptedException {
        ObjectSynchronizedTest obj = new ObjectSynchronizedTest();

        new Thread(() -> {
            try {
                System.out.println("a");
                obj.waitForStuffA();
                System.out.println("b");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(100);

        obj.waitLongerA();
    }

    private synchronized void waitForStuff() throws InterruptedException {
        Thread.sleep(300);

        this.i = 20;
    }

    @Outsource
    private void waitForStuffA() throws InterruptedException {
        waitForStuff();
    }

    @Outsource
    private synchronized void waitLonger() {
        if (i != 20) {
            throw new IllegalStateException();
        }
    }

    @Outsource
    private void waitLongerA() throws InterruptedException {
        waitLonger();
    }

}
