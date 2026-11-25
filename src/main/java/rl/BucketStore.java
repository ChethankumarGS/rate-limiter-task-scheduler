package rl;

/** Storage for token buckets. The limiter only needs one atomic update per request. */
public interface BucketStore {
    /** Atomically refill and try to take one token. Returns true if allowed. */
    boolean tryTake(String key, long nowNanos, double ratePerSec, double burst);
}
