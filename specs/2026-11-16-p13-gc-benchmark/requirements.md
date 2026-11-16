# Phase 13 Requirements - Garbage collector benchmark

## Goal
The collector for the matching path is chosen from measurements, with the limits of those measurements written down.

## Scope
- `GcLatencyBench` drives the real `OrderBook` with a deep book and allocation pressure; histogram-based so measuring does not load the heap
- `tools/gc-bench.sh` runs G1, Parallel, generational ZGC and Shenandoah and prints a table
- Decision recorded in `docs/GC-BENCHMARK.md`; the container uses ZGC
