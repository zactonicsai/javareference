package com.demo.concurrency.fixed;

import java.util.concurrent.TimeUnit;

/**
 * FIX: Either use `volatile` on the flag, or use the built-in
 * interrupt mechanism, which is the preferred idiom in modern Java.
 */
public class FixedStopFlag {

    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    public long runUntilStopped() {
        long iterations = 0;
        while (running) {
            iterations++;
        }
        return iterations;
    }

    /**
     * Even better: cooperate with thread interruption.
     * Works naturally with blocking calls (sleep, wait, IO) and is the
     * standard mechanism used across java.util.concurrent.
     */
    public static long runWithInterrupt() {
        long iterations = 0;
        while (!Thread.currentThread().isInterrupted()) {
            iterations++;
        }
        return iterations;
    }

    public static long demo(long stopAfterMillis) throws InterruptedException {
        FixedStopFlag flag = new FixedStopFlag();
        Thread worker = Thread.ofPlatform().start(flag::runUntilStopped);
        TimeUnit.MILLISECONDS.sleep(stopAfterMillis);
        flag.stop();
        worker.join(2000);
        return worker.isAlive() ? -1 : 0;
    }
}
