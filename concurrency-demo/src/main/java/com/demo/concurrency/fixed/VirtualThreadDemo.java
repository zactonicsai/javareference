package com.demo.concurrency.fixed;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Java 21 PREVIEW RELEASED AS STABLE: Virtual Threads (JEP 444).
 *
 * Key rules:
 *   - Virtual threads are CHEAP. Create one per task for blocking IO.
 *     Do NOT pool them -- pooling defeats the purpose.
 *   - DO NOT use `synchronized` around blocking IO in virtual-thread code.
 *     Pre-Java 24 this causes "pinning" -- the carrier platform thread is
 *     held hostage, blocking other virtual threads. Use ReentrantLock instead.
 *     (Java 24+ removes most pinning, but the safe habit is still ReentrantLock.)
 *   - Virtual threads shine for IO-bound work, not CPU-bound work.
 *     CPU-bound work still belongs on a fixed pool sized to core count.
 */
public class VirtualThreadDemo {

    /**
     * Simulates a workload of many concurrent blocking IO calls.
     * With platform threads, you hit memory/stack limits fast.
     * With virtual threads, scaling to 10k+ concurrent tasks is trivial.
     */
    public static Duration runBlockingWorkload(int taskCount, boolean useVirtual) throws Exception {
        ExecutorService executor = useVirtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(200);     // typical platform pool size

        Instant start = Instant.now();
        List<Future<?>> futures = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Thread.sleep(100);                    // simulate IO wait
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        return Duration.between(start, Instant.now());
    }
}
