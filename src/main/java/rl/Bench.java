package rl;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** In-process benchmark: throughput and per-call overhead of RateLimiter.allow(). */
public class Bench {
    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        RateLimiter limiter = new RateLimiter(new InMemoryStore(), 1_000_000, 2_000_000);
        // warm-up
        for (int i = 0; i < 2_000_000; i++) limiter.allow("k" + (i & 1023));
        AtomicLong total = new AtomicLong();
        long[][] samples = new long[threads][100_000];
        Thread[] ts = new Thread[threads];
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                long n = 0;
                int s = 0;
                String[] keys = new String[1024];
                for (int i = 0; i < keys.length; i++) keys[i] = "user-" + i;
                while (System.nanoTime() < end) {
                    long a = System.nanoTime();
                    limiter.allow(keys[(int) (n & 1023)]);
                    long d = System.nanoTime() - a;
                    if ((n & 63) == 0 && s < samples[id].length) samples[id][s++] = d;
                    n++;
                }
                total.addAndGet(n);
                samples[id] = Arrays.copyOf(samples[id], s);
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        double secs = (System.nanoTime() - start) / 1e9;
        long[] all = Arrays.stream(samples).flatMapToLong(Arrays::stream).sorted().toArray();
        System.out.printf("threads=%d calls=%d throughput=%.0f calls/s%n", threads, total.get(), total.get() / secs);
        System.out.printf("overhead p50=%.2f us p99=%.2f us p99.9=%.2f us%n",
            all[all.length / 2] / 1000.0, all[(int) (all.length * 0.99)] / 1000.0,
            all[(int) (all.length * 0.999)] / 1000.0);
    }
}
