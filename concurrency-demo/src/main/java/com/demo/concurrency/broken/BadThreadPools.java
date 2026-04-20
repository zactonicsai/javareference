package com.demo.concurrency.broken;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BROKEN: Common thread pool anti-patterns.
 *
 * 1. Executors.newCachedThreadPool() with no cap:
 *    Unbounded -- under load it creates threads until you OOM.
 *
 * 2. Executors.newSingleThreadExecutor():
 *    Unbounded queue -- tasks pile up silently; memory exhaustion follows.
 *
 * 3. Fixed pool sized to "big number I guessed":
 *    Too small for IO-bound work -> low throughput.
 *    Too large for CPU-bound work -> context-switching overhead.
 *
 * 4. Reusing one global pool for everything:
 *    Slow tasks starve fast ones. Unrelated features can deadlock each other.
 */
public class BadThreadPools {

    // DO NOT DO THIS in production
    public static ExecutorService unboundedCachedPool() {
        return Executors.newCachedThreadPool();          // unbounded thread count
    }

    public static ExecutorService unboundedQueue() {
        return Executors.newSingleThreadExecutor();      // unbounded task queue
    }
}
