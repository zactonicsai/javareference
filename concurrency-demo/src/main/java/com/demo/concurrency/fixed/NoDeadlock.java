package com.demo.concurrency.fixed;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIX: Two common approaches.
 *
 * 1. Consistent lock ordering: sort locks by identity (System.identityHashCode
 *    or a pre-assigned ID) and always acquire in the same order.
 *
 * 2. Use ReentrantLock.tryLock(timeout): if you can't get both locks within
 *    the budget, release what you hold and retry. Avoids deadlock entirely
 *    at the cost of a little retry complexity.
 */
public class NoDeadlock {

    private final ReentrantLock lockA = new ReentrantLock();
    private final ReentrantLock lockB = new ReentrantLock();

    public boolean doWork() throws InterruptedException {
        if (lockA.tryLock(500, TimeUnit.MILLISECONDS)) {
            try {
                if (lockB.tryLock(500, TimeUnit.MILLISECONDS)) {
                    try {
                        // ... work safely with both locks
                        return true;
                    } finally {
                        lockB.unlock();
                    }
                }
            } finally {
                lockA.unlock();
            }
        }
        return false;        // caller should back off and retry
    }

    /**
     * Alternative: consistent ordering based on identity.
     */
    public void transfer(Account from, Account to, long amount) {
        Account first  = from.id() < to.id() ? from : to;
        Account second = from.id() < to.id() ? to   : from;
        synchronized (first) {
            synchronized (second) {
                from.debit(amount);
                to.credit(amount);
            }
        }
    }

    public static class Account {
        private final long id;
        private long balance;
        public Account(long id, long balance) { this.id = id; this.balance = balance; }
        public long id() { return id; }
        public void debit(long n)  { balance -= n; }
        public void credit(long n) { balance += n; }
        public long balance() { return balance; }
    }
}
