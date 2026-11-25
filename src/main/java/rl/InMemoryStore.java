package rl;

import java.util.concurrent.ConcurrentHashMap;

/** Single-node store. compute() makes the refill-and-take step atomic per key. */
public class InMemoryStore implements BucketStore {
    private static final class Bucket {
        double tokens;
        long lastNanos;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryTake(String key, long nowNanos, double ratePerSec, double burst) {
        boolean[] allowed = new boolean[1];
        buckets.compute(key, (k, b) -> {
            if (b == null) {
                b = new Bucket();
                b.tokens = burst;
                b.lastNanos = nowNanos;
            }
            double elapsed = Math.max(0, nowNanos - b.lastNanos) / 1e9;
            b.tokens = Math.min(burst, b.tokens + elapsed * ratePerSec);
            b.lastNanos = nowNanos;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                allowed[0] = true;
            }
            return b;
        });
        return allowed[0];
    }

    public int size() {
        return buckets.size();
    }
}
