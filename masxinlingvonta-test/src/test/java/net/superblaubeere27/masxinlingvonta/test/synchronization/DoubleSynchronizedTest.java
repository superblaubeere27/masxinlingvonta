package net.superblaubeere27.masxinlingvonta.test.synchronization;

import net.superblaubeere27.masxinlingvonta.annotation.Outsource;

public class DoubleSynchronizedTest {
    private int i = 0;

    public static void main(String[] args) throws InterruptedException {
        DoubleSynchronizedTest obj = new DoubleSynchronizedTest();

        synchronized (obj) {
            synchronized (obj) {
                obj.doStuffA();
            }

            obj.wait(50);
        }
    }

    private void doStuff() {
        this.i = 0;

        try {
            wait(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Outsource
    private void doStuffA() {
        doStuff();
    }

}
