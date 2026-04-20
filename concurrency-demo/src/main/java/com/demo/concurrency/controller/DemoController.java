package com.demo.concurrency.controller;

import com.demo.concurrency.broken.*;
import com.demo.concurrency.fixed.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One endpoint per demo. Each response is a small JSON object summarizing
 * the outcome so you can compare broken vs fixed side-by-side from curl.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    // ---------- Singleton ----------

    @GetMapping("/singleton/broken")
    public Map<String, Object> singletonBroken() {
        BrokenSingleton s = BrokenSingleton.get();
        return result(
                "pattern", "Double-checked locking WITHOUT volatile",
                "risk",    "Partial construction visible to readers on weak memory models (ARM/POWER) or after JIT reordering",
                "payload", s.getPayload()
        );
    }

    @GetMapping("/singleton/fixed-holder")
    public Map<String, Object> singletonHolder() {
        return result(
                "pattern", "Initialization-on-demand holder idiom",
                "why",     "Lazy + thread-safe via JVM class-init guarantees, no volatile or synchronized needed",
                "payload", HolderSingleton.get().getPayload()
        );
    }

    @GetMapping("/singleton/fixed-volatile")
    public Map<String, Object> singletonVolatile() {
        return result(
                "pattern", "Double-checked locking WITH volatile",
                "why",     "volatile establishes happens-before so readers see a fully-constructed object",
                "payload", VolatileDclSingleton.get().getPayload()
        );
    }

    // ---------- Counter ----------

    @GetMapping("/counter/broken")
    public Map<String, Object> counterBroken(@RequestParam(defaultValue = "8") int threads,
                                             @RequestParam(defaultValue = "100000") int perThread) throws Exception {
        long expected = (long) threads * perThread;
        long actual = BrokenCounter.runDemo(threads, perThread);
        return result(
                "pattern",   "count++ on a plain long",
                "expected",  expected,
                "actual",    actual,
                "lost",      expected - actual,
                "note",      "Lost updates are non-deterministic. Run a few times to see variance."
        );
    }

    @GetMapping("/counter/fixed")
    public Map<String, Object> counterFixed(@RequestParam(defaultValue = "8") int threads,
                                            @RequestParam(defaultValue = "100000") int perThread,
                                            @RequestParam(defaultValue = "false") boolean useAdder) throws Exception {
        long expected = (long) threads * perThread;
        long actual = AtomicCounter.runDemo(threads, perThread, useAdder);
        return result(
                "pattern",   useAdder ? "LongAdder" : "AtomicLong",
                "expected",  expected,
                "actual",    actual,
                "lost",      expected - actual
        );
    }

    // ---------- Map ----------

    @GetMapping("/map/broken")
    public Map<String, Object> mapBroken(@RequestParam(defaultValue = "8") int threads,
                                         @RequestParam(defaultValue = "10000") int perThread) throws Exception {
        int expected = threads * perThread;
        try {
            int size = BrokenMapUsage.runDemo(threads, perThread).size();
            return result(
                    "pattern",  "HashMap under concurrent writes",
                    "expected", expected,
                    "actual",   size,
                    "lost",     expected - size,
                    "note",     "May also throw or hang on some JVMs. Size mismatch = lost puts or corrupted size."
            );
        } catch (Throwable t) {
            return result(
                    "pattern", "HashMap under concurrent writes",
                    "error",   t.getClass().getSimpleName() + ": " + t.getMessage(),
                    "note",    "Concurrent mutation corrupted the map's internal state."
            );
        }
    }

    @GetMapping("/map/fixed")
    public Map<String, Object> mapFixed(@RequestParam(defaultValue = "8") int threads,
                                        @RequestParam(defaultValue = "10000") int perThread) throws Exception {
        int expected = threads * perThread;
        int size = ConcurrentMapUsage.runDemo(threads, perThread).size();
        return result(
                "pattern",  "ConcurrentHashMap",
                "expected", expected,
                "actual",   size
        );
    }

    // ---------- Stop flag ----------

    @GetMapping("/flag/broken")
    public Map<String, Object> flagBroken(@RequestParam(defaultValue = "500") long stopAfterMs) throws Exception {
        long rc = BrokenStopFlag.demo(stopAfterMs);
        return result(
                "pattern", "Non-volatile boolean flag",
                "result",  rc == -1 ? "WORKER DID NOT STOP (JIT hoisted read out of loop)" : "worker stopped",
                "note",    "May stop on some JVMs/architectures. Run with -XX:+PrintCompilation to see the JIT kick in."
        );
    }

    @GetMapping("/flag/fixed")
    public Map<String, Object> flagFixed(@RequestParam(defaultValue = "500") long stopAfterMs) throws Exception {
        long rc = FixedStopFlag.demo(stopAfterMs);
        return result(
                "pattern", "volatile boolean flag",
                "result",  rc == -1 ? "worker did not stop" : "worker stopped cleanly"
        );
    }

    // ---------- Deadlock ----------

    @GetMapping("/deadlock/broken")
    public Map<String, Object> deadlockBroken() throws Exception {
        boolean deadlocked = DeadlockDemo.demo();
        return result(
                "pattern", "Inconsistent lock ordering",
                "result",  deadlocked ? "DEADLOCK DETECTED (threads did not complete in 3s)" : "no deadlock this run",
                "note",    "synchronized blocks don't respond to interrupt -- threads leak."
        );
    }

    // ---------- Virtual threads ----------

    @GetMapping("/vthreads/compare")
    public Map<String, Object> virtualThreads(@RequestParam(defaultValue = "2000") int tasks) throws Exception {
        Duration platform = VirtualThreadDemo.runBlockingWorkload(tasks, false);
        Duration virtual  = VirtualThreadDemo.runBlockingWorkload(tasks, true);
        return result(
                "tasks",            tasks,
                "platformPoolMs",   platform.toMillis(),
                "virtualThreadMs",  virtual.toMillis(),
                "speedup",          String.format("%.2fx", (double) platform.toMillis() / Math.max(1, virtual.toMillis())),
                "note",             "Each task sleeps 100ms. Platform pool (200 threads) serializes; virtual threads run all in parallel."
        );
    }

    // ---------- Fan-out ----------

    @GetMapping("/fanout/compare")
    public Map<String, Object> fanOut(@RequestParam(defaultValue = "user-42") String userId) throws Exception {
        Duration seq = ParallelFanOut.timeSequential(userId);
        Duration par = ParallelFanOut.timeParallel(userId);
        return result(
                "userId",        userId,
                "sequentialMs",  seq.toMillis(),
                "parallelMs",    par.toMillis(),
                "note",          "Three 100ms downstream calls. Sequential ~300ms, parallel ~100ms."
        );
    }

    // ---------- Info ----------

    @GetMapping("/info")
    public Map<String, Object> info() {
        return result(
                "javaVersion",     System.getProperty("java.version"),
                "vmName",          System.getProperty("java.vm.name"),
                "availableCores",  Runtime.getRuntime().availableProcessors(),
                "virtualThreads",  System.getProperty("spring.threads.virtual.enabled", "unset"),
                "endpoints", Map.ofEntries(
                        Map.entry("/demo/singleton/broken",        "DCL without volatile"),
                        Map.entry("/demo/singleton/fixed-holder",  "Holder idiom"),
                        Map.entry("/demo/singleton/fixed-volatile","Correct DCL"),
                        Map.entry("/demo/counter/broken",          "count++ race"),
                        Map.entry("/demo/counter/fixed",           "AtomicLong / LongAdder"),
                        Map.entry("/demo/map/broken",              "HashMap race"),
                        Map.entry("/demo/map/fixed",               "ConcurrentHashMap"),
                        Map.entry("/demo/flag/broken",             "Non-volatile flag"),
                        Map.entry("/demo/flag/fixed",              "Volatile flag"),
                        Map.entry("/demo/deadlock/broken",         "Lock-ordering deadlock"),
                        Map.entry("/demo/vthreads/compare",        "Virtual vs platform threads"),
                        Map.entry("/demo/fanout/compare",          "Sequential vs parallel IO")
                )
        );
    }

    // helper -- ordered map
    private static Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
