package com.demo.concurrency.fixed;

/**
 * FIX #1: Initialization-on-Demand Holder Idiom.
 *
 * Preferred over double-checked locking because:
 *   - No `volatile`, no `synchronized`, no double-check logic
 *   - The JVM guarantees class initialization is thread-safe and lazy
 *   - Holder class is only loaded when get() is first called
 *
 * This is the canonical way to do a lazy, thread-safe singleton in Java.
 */
public class HolderSingleton {

    private final String payload;

    private HolderSingleton() {
        this.payload = "initialized-" + System.nanoTime();
    }

    private static class Holder {
        static final HolderSingleton INSTANCE = new HolderSingleton();
    }

    public static HolderSingleton get() {
        return Holder.INSTANCE;
    }

    public String getPayload() {
        return payload;
    }
}
