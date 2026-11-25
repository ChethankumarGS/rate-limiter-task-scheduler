package rl;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Plain main-based tests so the project builds with only a JDK. */
public class Tests {
    static int failed = 0;

    static void check(boolean cond, String name) {
        System.out.println((cond ? "PASS " : "FAIL ") + name);
        if (!cond) failed++;
    }

    public static void main(String[] a) throws Exception {
        burstThenLimit();
        refillOverTime();
        keysAreIndependent();
        noOverAdmitUnderContention();
        retryThenAck();
        deadLetterAfterMaxAttempts();
        crashedWorkerTaskReappears();
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }

    static void burstThenLimit() {
        AtomicLong now = new AtomicLong();
        RateLimiter l = new RateLimiter(new InMemoryStore(), 10, 5, now::get);
        int ok = 0;
        for (int i = 0; i < 20; i++) if (l.allow("a")) ok++;
        check(ok == 5, "burst of 5 then blocked");
    }

    static void refillOverTime() {
        AtomicLong now = new AtomicLong();
        RateLimiter l = new RateLimiter(new InMemoryStore(), 10, 5, now::get);
        for (int i = 0; i < 5; i++) l.allow("a");
        check(!l.allow("a"), "empty bucket blocks");
        now.addAndGet(500_000_000L); // 0.5s -> 5 tokens
        int ok = 0;
        for (int i = 0; i < 10; i++) if (l.allow("a")) ok++;
        check(ok == 5, "refills 5 tokens after 0.5s");
    }

    static void keysAreIndependent() {
        AtomicLong now = new AtomicLong();
        RateLimiter l = new RateLimiter(new InMemoryStore(), 1, 1, now::get);
        check(l.allow("a") && l.allow("b") && !l.allow("a"), "separate buckets per key");
    }

    static void noOverAdmitUnderContention() throws Exception {
        AtomicLong now = new AtomicLong(); // frozen clock: no refill
        RateLimiter l = new RateLimiter(new InMemoryStore(), 1, 1000, now::get);
        AtomicInteger ok = new AtomicInteger();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 5000; j++) if (l.allow("hot")) ok.incrementAndGet(); });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        check(ok.get() == 1000, "exactly burst admitted across 8 threads (got " + ok.get() + ")");
    }

    static void retryThenAck() {
        TaskQueue q = new TaskQueue(1000, 3);
        long id = q.enqueue("x");
        TaskQueue.Task t = q.receive();
        q.fail(t.id);
        TaskQueue.Task t2 = q.receive();
        check(t2 != null && t2.id == id && t2.attempts == 2, "failed task is retried");
        check(q.ack(t2.id) && q.readyCount() == 0 && q.inFlightCount() == 0, "ack removes task");
    }

    static void deadLetterAfterMaxAttempts() {
        TaskQueue q = new TaskQueue(1000, 2);
        q.enqueue("x");
        q.fail(q.receive().id);
        q.fail(q.receive().id);
        check(q.deadLetterCount() == 1 && q.receive() == null, "dead-lettered after 2 attempts");
    }

    static void crashedWorkerTaskReappears() {
        AtomicLong now = new AtomicLong();
        TaskQueue q = new TaskQueue(1000, 3, now::get);
        q.enqueue("x");
        check(q.receive() != null, "first receive");
        check(q.receive() == null, "invisible while in flight");
        now.addAndGet(1_500_000_000L); // worker died, timeout passes
        TaskQueue.Task t = q.receive();
        check(t != null && t.attempts == 2, "task reappears after visibility timeout");
    }
}
