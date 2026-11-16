#!/usr/bin/env bash
# Runs the matching-path benchmark once per collector and prints a Markdown table. Needs a built tree: ./mvnw -q test-compile
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA="${JAVA_HOME:?set JAVA_HOME to a JDK 25}/bin/java"
WARM="${1:-15}"; MEASURE="${2:-45}"
CP="target/classes:target/test-classes"
echo "| Collector | ops | p50 µs | p99 µs | p99.9 µs | p99.99 µs | max µs | hiccup p99.9 ms | hiccup max ms |"
echo "|---|---|---|---|---|---|---|---|---|"
for gc in "G1:-XX:+UseG1GC" "Parallel:-XX:+UseParallelGC" "ZGC (generational):-XX:+UseZGC" "Shenandoah:-XX:+UseShenandoahGC"; do
  name="${gc%%:*}"; flag="${gc#*:}"
  out=$("$JAVA" -Xms1g -Xmx1g -XX:+UseCompactObjectHeaders $flag -cp "$CP" dev.brokerdesk.bench.GcLatencyBench "$WARM" "$MEASURE" 2>&1 | tail -1)
  echo "$out" | sed -E "s/ops=([0-9]+) +op_us p50=([0-9.]+) p99=([0-9.]+) p99.9=([0-9.]+) p99.99=([0-9.]+) max=([0-9.]+) \| hiccup_ms p99=[0-9.]+ p99.9=([0-9.]+) max=([0-9.]+).*/| $name | \1 | \2 | \3 | \4 | \5 | \6 | \7 | \8 |/"
done
