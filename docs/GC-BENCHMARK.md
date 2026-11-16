# Garbage collector choice for the matching path

A pause in the collector is a pause in order matching, so the collector is a design decision here, not a default.

## What was measured
`tools/gc-bench.sh` runs `dev.brokerdesk.bench.GcLatencyBench` once per collector (JDK 25, `-Xms1g -Xmx1g`, compact object headers, 15 s warm-up, 40 s measured).
It drives the **real `OrderBook`** with 200,000 resting orders, adds request-sized garbage and a ~100 MB working set that keeps being replaced (so objects get promoted and the collector has real work), and records:

- the latency of every `OrderBook.submit` into a small log-linear histogram (the histogram is a few KB, so measuring does not load the collector);
- **hiccups**: how much longer than 1 ms a sleeping thread takes to wake. Stop-the-world pauses show up here, the same idea as jHiccup.

## Result (one run, an Apple-silicon laptop, 2026-11-16)

| Collector | ops | p50 µs | p99 µs | p99.9 µs | p99.99 µs | max µs | hiccup p99.9 ms | hiccup max ms |
|---|---|---|---|---|---|---|---|---|
| G1 | 181,322,943 | 0.04 | 0.21 | 0.7 | 4.0 | 10,850.8 | 3.44 | 10.66 |
| Parallel | 158,596,520 | 0.04 | 0.21 | 0.5 | 3.4 | 9,648.9 | 7.41 | 10.71 |
| ZGC (generational) | 165,580,463 | 0.04 | 0.50 | 2.1 | 31.2 | 3,606.7 | 0.33 | 8.40 |
| Shenandoah | 164,897,865 | 0.04 | 0.25 | 0.7 | 5.1 | 9,437.3 | 0.42 | 7.70 |

## What it says, and what it does not
- Typical latency (p50) is the same for every collector: the matching code itself is fast and allocation-light.
- **The tail of pauses is where they differ.** The 99.9th-percentile hiccup is about **10x lower with ZGC (0.33 ms) and Shenandoah (0.42 ms) than with G1 (3.44 ms)**, and the worst single `submit` was about 3x lower with ZGC.
- ZGC pays for it with a heavier read barrier: its per-operation p99.99 is higher (31 µs against 4 µs for G1). That is the trade: fewer long stalls, slightly slower extremes per operation.
- The very largest hiccups (8 to 11 ms) are similar across collectors in this run. On a laptop that is most likely operating-system scheduling, so the maximum is **not** evidence either way.

## Decision
The container uses **generational ZGC** (`-XX:+UseZGC`, the only ZGC mode in JDK 25) because for an order desk a stall of several milliseconds every so often matters more than a few microseconds of extra cost per operation.
Shenandoah is a close second and a reasonable alternative.

## Caveats (please read before quoting numbers)
- One run per collector, on a laptop with other processes running. It is indicative, not a certified result.
- A micro-benchmark of the in-memory book, **not** the HTTP and database path, where the database dominates latency.
- Re-run it on the target hardware and heap size before deciding anything for production: `JAVA_HOME=... tools/gc-bench.sh 15 40`.
