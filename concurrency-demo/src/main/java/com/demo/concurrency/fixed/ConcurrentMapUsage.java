package com.demo.concurrency.fixed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FIX: ConcurrentHashMap.
 *
 * - Lock striping / CAS internally -- high throughput under contention
 * - `compute`, `computeIfAbsent`, `merge` are atomic per-key -- use them
 *   instead of get-then-put sequences
 * - Iterators are weakly consistent: no ConcurrentModificationException,
 *   but may reflect writes that happened after iteration started
 */
public class ConcurrentMapUsage {

    public static Map<Integer, Integer> runDemo(int threads, int entriesPerThread)
            throws InterruptedException {
        Map<Integer, Integer> map = new ConcurrentHashMap<>();
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

    /**
     * Common anti-pattern even with ConcurrentHashMap: check-then-act.
     * This is STILL a race -- another thread can put between containsKey and put.
     */
    public static void brokenCheckThenAct(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key)) {      // race window
            map.put(key, value);
        }
    }

    /**
     * Atomic alternative.
     */
    public static String atomicPutIfAbsent(Map<String, String> map, String key, String value) {
        return map.putIfAbsent(key, value);   // atomic
    }

    /**
     * For lazy, expensive initialization per key.
     */
    public static String atomicComputeIfAbsent(ConcurrentHashMap<String, String> map, String key) {
        return map.computeIfAbsent(key, k -> expensiveLookup(k));    // atomic, called once per key
    }

    private static String expensiveLookup(String k) {
        return "value-for-" + k;
    }
}
