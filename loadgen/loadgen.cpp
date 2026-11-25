// Minimal HTTP load generator: N threads, keep-alive connections, GET /check.
// Usage: ./loadgen host port threads seconds
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <unistd.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

using clk = std::chrono::steady_clock;

static void worker(const char* host, int port, int id, int seconds,
                   std::atomic<long>& ok, std::atomic<long>& limited,
                   std::vector<double>& lat) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    sockaddr_in a{};
    a.sin_family = AF_INET;
    a.sin_port = htons(port);
    inet_pton(AF_INET, host, &a.sin_addr);
    if (connect(fd, (sockaddr*)&a, sizeof a) != 0) { perror("connect"); return; }
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
    auto end = clk::now() + std::chrono::seconds(seconds);
    char buf[4096];
    long n = 0;
    while (clk::now() < end) {
        std::string req = "GET /check?key=user-" + std::to_string(id * 1000 + n % 500) +
                          " HTTP/1.1\r\nHost: x\r\n\r\n";
        auto t0 = clk::now();
        if (send(fd, req.data(), req.size(), 0) < 0) break;
        ssize_t r = recv(fd, buf, sizeof buf - 1, 0);
        if (r <= 0) break;
        auto t1 = clk::now();
        buf[r] = 0;
        if (strstr(buf, " 200 ")) ok++; else if (strstr(buf, " 429 ")) limited++;
        if (n % 8 == 0) lat.push_back(std::chrono::duration<double, std::milli>(t1 - t0).count());
        n++;
    }
    close(fd);
}

int main(int argc, char** argv) {
    if (argc < 5) { fprintf(stderr, "usage: %s host port threads seconds\n", argv[0]); return 1; }
    int port = atoi(argv[2]), threads = atoi(argv[3]), seconds = atoi(argv[4]);
    std::atomic<long> ok{0}, limited{0};
    std::vector<std::vector<double>> lats(threads);
    std::vector<std::thread> ts;
    auto s = clk::now();
    for (int i = 0; i < threads; i++)
        ts.emplace_back(worker, argv[1], port, i, seconds, std::ref(ok), std::ref(limited), std::ref(lats[i]));
    for (auto& t : ts) t.join();
    double secs = std::chrono::duration<double>(clk::now() - s).count();
    std::vector<double> all;
    for (auto& v : lats) all.insert(all.end(), v.begin(), v.end());
    std::sort(all.begin(), all.end());
    long total = ok + limited;
    printf("requests=%ld allowed=%ld limited=%ld throughput=%.0f req/s\n", total, ok.load(), limited.load(), total / secs);
    if (!all.empty())
        printf("round-trip p50=%.3f ms p99=%.3f ms\n", all[all.size() / 2], all[(size_t)(all.size() * 0.99)]);
    return 0;
}
