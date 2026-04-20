package com.demo.concurrency.broken;

import java.util.concurrent.TimeUnit;

/**
 * BROKEN: Non-volatile stop flag.
 *
 * The JIT can hoist the read of `running` out of the loop because from
 * a single thread's perspective nothing inside the loop changes it.
 * Result: the worker thread never observes the flag flip and spins forever.
 *
 * This is the textbook case where `volatile` is exactly the right tool.
 */
public class BrokenStopFlag {

    private boolean running = true;       // should be volatile

    public void stop() {
        running = false;
    }

    public long runUntilStopped() {
        long iterations = 0;
        while (running) {
            iterations++;
            // Note: Thread.sleep, synchronized blocks, or any volatile
            // read would accidentally fix this by acting as a memory barrier.
            // That's why this bug often hides until you optimize the loop.
        }
        return iterations;
    }

    public static long demo(long stopAfterMillis) throws InterruptedException {
        BrokenStopFlag flag = new BrokenStopFlag();
        Thread worker = Thread.ofPlatform().start(flag::runUntilStopped);
        TimeUnit.MILLISECONDS.sleep(stopAfterMillis);
        flag.stop();
        worker.join(2000);                // may time out -- worker likely still spinning
        if (worker.isAlive()) {
            worker.interrupt();           // forceful cleanup for demo
            return -1;                    // sentinel: worker did not observe stop
        }
        return 0;
    }
}
