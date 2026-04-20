# Concurrency Demo — Spring Boot 3.5.13 on Java 21

A runnable catalog of Java concurrency pitfalls and their fixes, each exposed as an HTTP endpoint so you can compare broken vs. fixed behavior side by side.

## Quick start

```bash
docker compose up --build
# first build pulls the Gradle image and downloads dependencies -- give it 2-3 minutes

# then, in another terminal:
curl -s localhost:8080/demo/info | jq
```

## The demos

| Endpoint | What it shows |
|---|---|
| `GET /demo/singleton/broken` | Double-checked locking **without** `volatile` — the unsafe-publication bug |
| `GET /demo/singleton/fixed-holder` | Initialization-on-demand holder idiom (preferred) |
| `GET /demo/singleton/fixed-volatile` | DCL done correctly with `volatile` |
| `GET /demo/counter/broken` | `count++` race — lost updates you can actually observe |
| `GET /demo/counter/fixed` | `AtomicLong`, or `LongAdder` with `?useAdder=true` |
| `GET /demo/map/broken` | `HashMap` under concurrent writes |
| `GET /demo/map/fixed` | `ConcurrentHashMap` |
| `GET /demo/flag/broken` | Non-`volatile` stop flag — the JIT hoists the read and the worker never stops |
| `GET /demo/flag/fixed` | `volatile` flag + interrupt-based cancellation |
| `GET /demo/deadlock/broken` | Deadlock via inconsistent lock ordering |
| `GET /demo/vthreads/compare` | Virtual vs platform threads for 2000 blocking tasks |
| `GET /demo/fanout/compare` | Sequential vs parallel downstream IO |

## Reproducing the bugs

### Lost updates (counter race)

```bash
curl -s 'localhost:8080/demo/counter/broken?threads=8&perThread=200000' | jq
```

Sample output:
```json
{
  "pattern": "count++ on a plain long",
  "expected": 1600000,
  "actual": 1482310,
  "lost": 117690,
  "note": "Lost updates are non-deterministic. Run a few times to see variance."
}
```

The fixed version never loses an increment:

```bash
curl -s 'localhost:8080/demo/counter/fixed?threads=8&perThread=200000' | jq
```

### Virtual thread speedup

```bash
curl -s 'localhost:8080/demo/vthreads/compare?tasks=2000' | jq
```

With 2000 tasks, each sleeping 100 ms:
- Fixed platform pool of 200 threads → ~1000 ms (10 rounds of 200)
- Virtual threads → ~100 ms (all run concurrently)

### Fan-out speedup

```bash
curl -s 'localhost:8080/demo/fanout/compare' | jq
```

Three 100 ms calls: sequential ≈ 300 ms, parallel ≈ 100 ms.

### The broken stop flag

```bash
curl -s 'localhost:8080/demo/flag/broken' | jq
```

May or may not reproduce depending on how aggressively the JIT has compiled the loop. For a more reliable repro, uncomment the `PrintCompilation` flags in `docker-compose.yml` and watch the log — once you see `BrokenStopFlag::runUntilStopped` get compiled (C2), subsequent runs will spin forever on the pre-flag-flip value.

## Concepts covered

1. **Unsafe publication (DCL without `volatile`)** — the original question. The JMM allows reordering inside `new Foo()`, so readers on the fast path can see a non-null reference to a partially-constructed object. Fix: `volatile` or use the holder idiom.

2. **Lost updates on `x++`** — read-modify-write is three operations, not one. Fix: `AtomicLong` for low-to-medium contention, `LongAdder` for high write contention with rare reads.

3. **`HashMap` vs `ConcurrentHashMap`** — plain `HashMap` is not thread-safe under writes. Even with `Collections.synchronizedMap`, check-then-act sequences like `if (!map.containsKey(k)) map.put(k, v)` are races. Use `putIfAbsent` / `computeIfAbsent`.

4. **JIT loop hoisting + missing `volatile`** — reads of a non-`volatile` field inside a tight loop can be hoisted out. The worker never sees a flag flip. Fix: `volatile`, or cooperate with `Thread.interrupt()`.

5. **Deadlock via inconsistent lock ordering** — always acquire locks in a globally consistent order, or use `ReentrantLock.tryLock(timeout)` so you can back off. `synchronized` blocks cannot be interrupted, so a deadlock there leaks threads permanently.

6. **Thread pool anti-patterns** — unbounded `newCachedThreadPool`, unbounded queues, one giant shared pool for everything. Use bounded pools sized to workload type (cores for CPU-bound; virtual threads for IO-bound), name your threads for observability, pick a rejection policy.

7. **Virtual threads (JEP 444, stable in Java 21)** — millions of cheap threads for blocking IO. Do **not** pool them. Avoid `synchronized` around blocking calls pre-Java-24 (pinning); prefer `ReentrantLock`. CPU-bound work still belongs on platform thread pools.

8. **Parallel fan-out** — don't stack latencies. Independent downstream calls should run concurrently. `CompletableFuture` + virtual thread executor works today; `StructuredTaskScope` is cleaner when you move to Java 25+.

## Project layout

```
src/main/java/com/demo/concurrency/
├── ConcurrencyDemoApplication.java
├── broken/
│   ├── BrokenSingleton.java       DCL without volatile
│   ├── BrokenCounter.java         lost updates
│   ├── BrokenMapUsage.java        HashMap race
│   ├── BrokenStopFlag.java        non-volatile flag
│   ├── DeadlockDemo.java          lock-ordering deadlock
│   └── BadThreadPools.java        unbounded pools
├── fixed/
│   ├── HolderSingleton.java       holder idiom
│   ├── VolatileDclSingleton.java  correct DCL
│   ├── AtomicCounter.java         AtomicLong + LongAdder
│   ├── ConcurrentMapUsage.java    ConcurrentHashMap
│   ├── FixedStopFlag.java         volatile + interrupt
│   ├── NoDeadlock.java            tryLock + consistent ordering
│   ├── GoodThreadPools.java       bounded pools
│   ├── VirtualThreadDemo.java     Java 21 virtual threads
│   └── ParallelFanOut.java        concurrent fan-out
└── controller/
    └── DemoController.java
```

## Running locally without Docker

Requires JDK 21.

```bash
# If you have Gradle installed:
gradle bootRun

# Or let the Docker build produce the jar and run it directly:
docker compose up --build
```

## Observability

Actuator is enabled:

```bash
curl -s localhost:8080/actuator/health | jq
curl -s localhost:8080/actuator/threaddump | jq '.threads[] | .threadName' | sort -u
```

The thread dump is useful for confirming virtual threads are being used for request handling (you'll see names like `VirtualThread-...`).
