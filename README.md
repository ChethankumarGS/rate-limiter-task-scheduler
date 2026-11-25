# Rate Limiter and Fault-Tolerant Task Scheduler

A small Java service with two parts I wanted to understand properly: a token bucket rate limiter and a task queue that survives worker crashes. A C++ load generator drives it over HTTP.

## What is in here

**Rate limiter** (`RateLimiter`, `BucketStore`, `InMemoryStore`)
- Token bucket per key, with refill and take done as one atomic step.
- Storage sits behind the `BucketStore` interface. Only the in-memory store is written. A shared store (DynamoDB, Redis) would implement the same one method, and that is not built yet.
- A test checks that 8 threads hammering one key never admit more than the burst size.

**Task scheduler** (`TaskQueue`, `Worker`)
- Queue behaves like SQS: a received task is hidden for a visibility timeout. If the worker never acks (crash), the task comes back.
- Failed tasks are retried up to a max attempt count, then moved to a dead-letter list.

**HTTP server** (`Server`)
- `GET /check?key=abc` returns 200 or 429
- `POST /enqueue` adds a task
- `GET /stats` shows ready, in-flight and dead-letter counts

**Load generator** (`loadgen/loadgen.cpp`): keep-alive connections, one thread per connection, reports throughput and round-trip latency.

## Run it

Needs a JDK (17 used) and g++.

```
./build.sh
java -cp out rl.Tests                 # unit tests
java -cp out rl.Bench 2 5             # in-process benchmark, 2 threads, 5 s
java -cp out rl.Server 18080 100000000 200000000 &
g++ -O2 -std=c++17 -pthread -o loadgen/loadgen loadgen/loadgen.cpp
./loadgen/loadgen 127.0.0.1 18080 4 8
```

Docker: `docker build -t rate-limiter .` (written, not built by me yet).

## Measured numbers

Run on a 2-core cloud sandbox, server and load generator on the same machine, so treat them as a rough floor.

| Test | Result |
| --- | --- |
| HTTP, 4 connections, 8 s | 27,909 req/s (223,288 requests) |
| HTTP round trip | p50 0.12 ms, p99 0.57 ms |
| In-process `allow()`, 2 threads | 6.97M calls/s |
| In-process `allow()` overhead | p50 0.21 us, p99 0.57 us |

## Not done

- No distributed store yet, so limits are per process.
- No AWS deployment. The queue copies SQS behavior but does not use SQS.
- No auth on the HTTP endpoints.
