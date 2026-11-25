package rl;

import java.util.function.LongSupplier;

/** Token bucket limiter. The store decides where bucket state lives. */
public class RateLimiter {
    private final BucketStore store;
    private final double ratePerSec;
    private final double burst;
    private final LongSupplier clock;

    public RateLimiter(BucketStore store, double ratePerSec, double burst) {
        this(store, ratePerSec, burst, System::nanoTime);
    }

    public RateLimiter(BucketStore store, double ratePerSec, double burst, LongSupplier clock) {
        this.store = store;
        this.ratePerSec = ratePerSec;
        this.burst = burst;
        this.clock = clock;
    }

    public boolean allow(String key) {
        return store.tryTake(key, clock.getAsLong(), ratePerSec, burst);
    }
}
