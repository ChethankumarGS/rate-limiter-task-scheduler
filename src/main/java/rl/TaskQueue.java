package rl;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Queue with SQS-style semantics: a received task stays invisible until its
 * visibility timeout expires. If the worker does not ack in time (crash), the
 * task becomes visible again. After maxAttempts it moves to the dead-letter list.
 */
public class TaskQueue {
    public static final class Task {
        public final long id;
        public final String payload;
        public int attempts;
        long invisibleUntilNanos;
        Task(long id, String payload) { this.id = id; this.payload = payload; }
    }

    private final ArrayDeque<Task> ready = new ArrayDeque<>();
    private final Map<Long, Task> inFlight = new HashMap<>();
    private final java.util.List<Task> deadLetter = new java.util.ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final long visibilityNanos;
    private final int maxAttempts;
    private final LongSupplier clock;

    public TaskQueue(long visibilityMillis, int maxAttempts) {
        this(visibilityMillis, maxAttempts, System::nanoTime);
    }

    public TaskQueue(long visibilityMillis, int maxAttempts, LongSupplier clock) {
        this.visibilityNanos = visibilityMillis * 1_000_000L;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    public synchronized long enqueue(String payload) {
        Task t = new Task(ids.incrementAndGet(), payload);
        ready.add(t);
        return t.id;
    }

    /** Returns the next visible task or null. */
    public synchronized Task receive() {
        requeueExpired();
        Task t = ready.poll();
        if (t == null) return null;
        t.attempts++;
        t.invisibleUntilNanos = clock.getAsLong() + visibilityNanos;
        inFlight.put(t.id, t);
        return t;
    }

    public synchronized boolean ack(long id) {
        return inFlight.remove(id) != null;
    }

    /** Worker reports failure: retry now or dead-letter. */
    public synchronized void fail(long id) {
        Task t = inFlight.remove(id);
        if (t == null) return;
        if (t.attempts >= maxAttempts) deadLetter.add(t);
        else ready.add(t);
    }

    private void requeueExpired() {
        long now = clock.getAsLong();
        var it = inFlight.values().iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (now >= t.invisibleUntilNanos) {
                it.remove();
                if (t.attempts >= maxAttempts) deadLetter.add(t);
                else ready.add(t);
            }
        }
    }

    public synchronized int readyCount() { return ready.size(); }
    public synchronized int inFlightCount() { return inFlight.size(); }
    public synchronized int deadLetterCount() { return deadLetter.size(); }
}
