package com.demo.concurrency.broken;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * BROKEN: HashMap under concurrent write access.
 *
 * Possible outcomes:
 *   - Lost updates (last-write-wins per bucket)
 *   - ConcurrentModificationException during iteration
 *   - Historically: infinite loops from broken internal linkage during resize
 *     (less common in Java 8+ but still not thread-safe)
 *   - Corrupted size counter
 *
 * Wrapping with Collections.synchronizedMap is better than nothing but
 * locks the entire map and still breaks on iteration unless you externally
 * synchronize the iteration block.
 */
public class BrokenMapUsage {

    public static Map<Integer, Integer> runDemo(int threads, int entriesPerThread)
            throws InterruptedException {
        Map<Integer, Integer> map = new HashMap<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                final int offset = t * entriesPerThread;
                pool.submit(() -> {
                    for (int i = 0; i < entriesPerThread; i++) {
                        map.put(offset + i, i);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        return map;
    }
}
