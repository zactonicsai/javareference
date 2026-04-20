package com.demo.concurrency.broken;

/**
 * BROKEN: Double-Checked Locking without volatile.
 *
 * The JMM allows reordering of steps inside `new Cache()`:
 *   1. allocate memory
 *   2. run constructor (set `payload`)
 *   3. assign reference to `instance`
 *
 * Steps 2 and 3 can be reordered. A reader on the fast path (no lock)
 * can observe `instance != null` but see `payload == null` because the
 * constructor hasn't completed yet. This is called "unsafe publication".
 *
 * Hard to reproduce on x86 (TSO), but real on AArch64 / ARM / POWER and
 * after JIT optimization on any architecture.
 */
public class BrokenSingleton {

    // MISSING `volatile` -- this is the bug
    private static BrokenSingleton instance;

    private final String payload;

    private BrokenSingleton() {
        // Simulate expensive initialization
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.payload = "initialized-" + System.nanoTime();
    }

    public static BrokenSingleton get() {
        if (instance == null) {                    // check 1 (no lock)
            synchronized (BrokenSingleton.class) {
                if (instance == null) {            // check 2
                    instance = new BrokenSingleton();
                }
            }
        }
        return instance;
    }

    public String getPayload() {
        return payload;
    }

    // For demo purposes only -- reset between runs
    static void reset() {
        instance = null;
    }
}
