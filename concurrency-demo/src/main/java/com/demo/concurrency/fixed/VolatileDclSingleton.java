package com.demo.concurrency.fixed;

/**
 * FIX #2: Double-Checked Locking DONE RIGHT with `volatile`.
 *
 * Only use this if you have a reason to avoid the holder idiom
 * (e.g., initialization depends on runtime parameters).
 *
 * `volatile` establishes a happens-before relationship: the write
 * in the synchronized block cannot be reordered past other writes
 * inside the constructor, and readers on the fast path will see
 * a fully-constructed object.
 */
public class VolatileDclSingleton {

    private static volatile VolatileDclSingleton instance;   // volatile is the fix

    private final String payload;

    private VolatileDclSingleton() {
        this.payload = "initialized-" + System.nanoTime();
    }

    public static VolatileDclSingleton get() {
        VolatileDclSingleton local = instance;      // read volatile once
        if (local == null) {
            synchronized (VolatileDclSingleton.class) {
                local = instance;
                if (local == null) {
                    local = new VolatileDclSingleton();
                    instance = local;
                }
            }
        }
        return local;
    }

    public String getPayload() {
        return payload;
    }
}
