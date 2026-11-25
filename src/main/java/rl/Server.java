package rl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * GET  /check?key=abc  -> 200 allowed, 429 limited
 * POST /enqueue        -> body is the task payload, returns task id
 * GET  /stats          -> queue counters
 */
public class Server {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        double rate = args.length > 1 ? Double.parseDouble(args[1]) : 1000;
        double burst = args.length > 2 ? Double.parseDouble(args[2]) : 2000;
        RateLimiter limiter = new RateLimiter(new InMemoryStore(), rate, burst);
        TaskQueue queue = new TaskQueue(5000, 3);
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(new Worker(queue, p -> true), "worker-" + i);
            t.setDaemon(true);
            t.start();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 1024);
        server.createContext("/check", ex -> {
            String q = ex.getRequestURI().getQuery();
            String key = q != null && q.startsWith("key=") ? q.substring(4) : "anon";
            reply(ex, limiter.allow(key) ? 200 : 429, "");
        });
        server.createContext("/enqueue", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            reply(ex, 202, Long.toString(queue.enqueue(body)));
        });
        server.createContext("/stats", ex -> reply(ex, 200,
            "{\"ready\":" + queue.readyCount() + ",\"inFlight\":" + queue.inFlightCount()
            + ",\"deadLetter\":" + queue.deadLetterCount() + "}"));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("listening on " + port);
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }
}
