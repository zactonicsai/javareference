package com.demo.concurrency.fixed;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * FIX: Bounded pool, bounded queue, explicit rejection policy.
 *
 * Sizing rules of thumb:
 *   - CPU-bound:  N ≈ number of cores (Runtime.availableProcessors())
 *   - IO-bound:   N can be much larger, OR use virtual threads instead
 *   - Mixed:      profile and tune, or split into two pools
 *
 * Rejection policies:
 *   - AbortPolicy (default):  throws RejectedExecutionException -- loud failure
 *   - CallerRunsPolicy:       the submitting thread runs the task -- applies backpressure
 *   - DiscardPolicy:          silently drops -- only if you can tolerate loss
 *   - DiscardOldestPolicy:    evicts oldest queued task
 *
 * Separate pools for separate concerns -- never one giant shared pool.
 */
public class GoodThreadPools {

    /**
     * CPU-bound pool: number of cores, small queue.
     */
    public static ExecutorService cpuBoundPool(String name) {
        int cores = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cores, cores,
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1000),                         // BOUNDED queue
                Thread.ofPlatform().name(name + "-", 0).factory(),      // named for observability
                new ThreadPoolExecutor.CallerRunsPolicy()               // backpressure on overload
        );
    }

    /**
     * IO-bound pool: prefer virtual threads on Java 21+.
     */
    public static ExecutorService ioBoundExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
