package com.demo.concurrency.fixed;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * FIX: Use AtomicLong for correctness under contention,
 * or LongAdder when you only read the total occasionally --
 * LongAdder spreads writes across multiple cells for lower
 * contention (better throughput on many-core machines).
 */
public class AtomicCounter {
    private final AtomicLong atomic = new AtomicLong();
    private final LongAdder adder = new LongAdder();

    public void incrementAtomic() {
        atomic.incrementAndGet();
    }

    public void incrementAdder() {
        adder.increment();          // cheaper under heavy contention
    }

    public long getAtomic() { return atomic.get(); }
    public long getAdder()  { return adder.sum(); }

    public static long runDemo(int threads, int incrementsPerThread, boolean useAdder)
            throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        if (useAdder) counter.incrementAdder();
                        else          counter.incrementAtomic();
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        return useAdder ? counter.getAdder() : counter.getAtomic();
    }
}
