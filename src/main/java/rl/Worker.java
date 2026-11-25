package rl;

import java.util.function.Predicate;

/** Pulls tasks, runs the handler, acks on success, fails on false or exception. */
public class Worker implements Runnable {
    private final TaskQueue queue;
    private final Predicate<String> handler;
    private volatile boolean running = true;

    public Worker(TaskQueue queue, Predicate<String> handler) {
        this.queue = queue;
        this.handler = handler;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        while (running) {
            TaskQueue.Task t = queue.receive();
            if (t == null) {
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
                continue;
            }
            boolean ok;
            try { ok = handler.test(t.payload); } catch (RuntimeException e) { ok = false; }
            if (ok) queue.ack(t.id); else queue.fail(t.id);
        }
    }
}
