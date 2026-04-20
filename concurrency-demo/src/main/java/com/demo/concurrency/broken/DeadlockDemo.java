package com.demo.concurrency.broken;

import java.util.concurrent.TimeUnit;

/**
 * BROKEN: Deadlock via inconsistent lock acquisition order.
 *
 * Thread A locks X then tries Y.
 * Thread B locks Y then tries X.
 * Both wait forever.
 *
 * Fix: always acquire locks in a globally consistent order, or use
 * tryLock() with a timeout so you can back off and retry.
 */
public class DeadlockDemo {

    private final Object lockA = new Object();
    private final Object lockB = new Object();

    public void threadOne() {
        synchronized (lockA) {
            sleep(50);
            synchronized (lockB) {
                // ... work
            }
        }
    }

    public void threadTwo() {
        synchronized (lockB) {         // opposite order -- BAD
            sleep(50);
            synchronized (lockA) {
                // ... work
            }
        }
    }

    private static void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Returns true if deadlock was detected (threads didn't finish in time).
     */
    public static boolean demo() throws InterruptedException {
        DeadlockDemo d = new DeadlockDemo();
        Thread t1 = Thread.ofPlatform().start(d::threadOne);
        Thread t2 = Thread.ofPlatform().start(d::threadTwo);
        t1.join(3000);
        t2.join(3000);
        boolean deadlocked = t1.isAlive() || t2.isAlive();
        if (deadlocked) {
            t1.interrupt();           // synchronized blocks don't respond to interrupt;
            t2.interrupt();           // these threads leak. Use ReentrantLock.lockInterruptibly() in real code.
        }
        return deadlocked;
    }
}
