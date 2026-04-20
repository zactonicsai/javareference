package com.demo.concurrency.fixed;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FIX: Parallel fan-out done right.
 *
 * BAD: sequential calls add up latency. 3 calls * 100ms = 300ms.
 * GOOD: fan out in parallel, gather at the end.
 *
 * Legacy approach: CompletableFuture.supplyAsync + allOf.join
 *   - Works, but error handling and cancellation are awkward
 *   - If one call fails, siblings keep running -- wasted work
 *
 * Modern approach (Java 21 preview, stable in Java 25):
 *   StructuredTaskScope treats the parallel tasks as a unit:
 *   one fails -> siblings get cancelled automatically.
 *
 * This example uses CompletableFuture for portability. Switch to
 * StructuredTaskScope when you're on Java 25+ or enable preview.
 */
public class ParallelFanOut {

    record UserProfile(String name, String email, List<String> orders) {}

    public static UserProfile loadProfileParallel(String userId) throws Exception {
        try (ExecutorService ioPool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String>       nameF   = CompletableFuture.supplyAsync(() -> fetchName(userId), ioPool);
            CompletableFuture<String>       emailF  = CompletableFuture.supplyAsync(() -> fetchEmail(userId), ioPool);
            CompletableFuture<List<String>> ordersF = CompletableFuture.supplyAsync(() -> fetchOrders(userId), ioPool);

            CompletableFuture.allOf(nameF, emailF, ordersF)
                    .get(2, TimeUnit.SECONDS);    // overall timeout, not per-task

            return new UserProfile(nameF.get(), emailF.get(), ordersF.get());
        }
    }

    public static UserProfile loadProfileSequential(String userId) {
        String name        = fetchName(userId);
        String email       = fetchEmail(userId);
        List<String> orders = fetchOrders(userId);
        return new UserProfile(name, email, orders);
    }

    public static Duration timeParallel(String id)   throws Exception {
        Instant s = Instant.now();
        loadProfileParallel(id);
        return Duration.between(s, Instant.now());
    }

    public static Duration timeSequential(String id) {
        Instant s = Instant.now();
        loadProfileSequential(id);
        return Duration.between(s, Instant.now());
    }

    // --- fake downstream calls ---
    private static String fetchName(String id)    { sleep(100); return "Name("    + id + ")"; }
    private static String fetchEmail(String id)   { sleep(100); return "Email("   + id + ")"; }
    private static List<String> fetchOrders(String id) { sleep(100); return List.of("o1", "o2", "o3"); }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
