package com.demo.concurrency.broken;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BROKEN: `count++` is three operations: read, add, write.
 * Two threads can read the same value, both add 1, both write --
 * one increment is lost. Classic lost-update race.
 *
 * Also: without `volatile`, a reader may see stale values indefinitely.
 */
public class BrokenCounter {
    private long count = 0;

    public void increment() {
        count++;              // read-modify-write, not atomic
    }

    public long get() {
        return count;
    }

    public static long runDemo(int threads, int incrementsPerThread) throws InterruptedException {
        BrokenCounter counter = new BrokenCounter();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        counter.increment();
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        return counter.get();
    }
}
