package net.superblaubeere27.masxinlingvonta.test.synchronization;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class ObjectWaitLockTest {
    private boolean didShit = false;
    private int i = 0;

    public static void main(String[] args) throws InterruptedException {
        ObjectWaitLockTest obj = new ObjectWaitLockTest();

        // TODO: obj::threadMainA causes verification error (wtf?)
        new Thread(() -> obj.threadMainA()).start();

        new Thread(() -> {
            try {
                Thread.sleep(50);

                synchronized (obj) {
                    obj.didShit = true;

                    obj.notifyAll();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(100);

        obj.waitForShitA();
    }

    private synchronized void threadMain() throws InterruptedException {
        while (!this.didShit) {
            wait();
        }

        this.i = 10;
    }

    /*
     * Prevent the JVM from doing the synchronization for us.
     */

    private synchronized void waitForShit() {
        if (i != 10) {
            throw new IllegalStateException();
        }
    }

    @Outsource
    private void threadMainA() {
        try {
            threadMain();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Outsource
    private void waitForShitA() {
        waitForShit();
    }

}
