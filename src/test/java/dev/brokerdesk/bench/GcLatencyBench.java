package dev.brokerdesk.bench;

import dev.brokerdesk.domain.Side;
import dev.brokerdesk.engine.OrderBook;

import java.util.Arrays;
import java.util.Random;

/**
 * Indicative GC comparison for the matching path. Not a JMH benchmark and not the full HTTP path: it drives the real OrderBook with a large
 * resting book, adds request-sized garbage and a retained working set so the collector has real work, and records
 *  - latency of every OrderBook.submit (nanoseconds)
 *  - "hiccups": how much longer than 1 ms a sleeping thread takes to wake (stop-the-world pauses show up here, like jHiccup).
 * Usage: GcLatencyBench <warmupSeconds> <measureSeconds>. Run it once per collector (tools/gc-bench.sh).
 */
public final class GcLatencyBench {
    public static void main(String[] args) throws Exception {
        int warm = Integer.parseInt(args[0]), measure = Integer.parseInt(args[1]);
        var hiccups = new long[2_000_000];
        var hiccupCount = new int[1];
        var stop = new boolean[1];
        Thread detector = new Thread(() -> {
            try {
                while (!stop[0]) {
                    long t = System.nanoTime();
                    Thread.sleep(1);
                    long over = System.nanoTime() - t - 1_000_000;
                    if (hiccupCount[0] < hiccups.length) hiccups[hiccupCount[0]++] = Math.max(over, 0);
                }
            } catch (InterruptedException ignored) { }
        }, "hiccup");
        detector.setDaemon(true);

        OrderBook book = new OrderBook();
        Random rnd = new Random(7);
        long id = 1;
        for (int i = 0; i < 200_000; i++) {   // a deep resting book: 100k bids below 12.000 and 100k offers above 12.100
            boolean buy = i % 2 == 0;
            book.rest(id++, 1 + rnd.nextInt(5000), buy ? Side.BUY : Side.SELL, buy ? 12_000 - rnd.nextInt(2_000) : 12_100 + rnd.nextInt(2_000), 100 + rnd.nextInt(900));
        }
        byte[][] retained = new byte[50_000][];   // ~100 MB working set that keeps being replaced: promotes objects and forces old-generation work
        Hist lat = new Hist();   // a few KB: measuring must not itself load the collector
        long endWarm = System.nanoTime() + warm * 1_000_000_000L, end = endWarm + measure * 1_000_000_000L;
        boolean measuring = false;
        long sink = 0;
        while (true) {
            long now = System.nanoTime();
            if (!measuring && now >= endWarm) { measuring = true; detector.start(); }
            if (now >= end) break;
            retained[rnd.nextInt(retained.length)] = new byte[2_048];   // request-sized garbage that sometimes survives
            boolean buy = rnd.nextBoolean();
            long price = 11_950 + rnd.nextInt(300);
            long t0 = System.nanoTime();
            var m = book.submit(id++, 6000 + rnd.nextInt(5000), buy ? Side.BUY : Side.SELL, price, 1 + rnd.nextInt(200));
            long dt = System.nanoTime() - t0;
            sink += m.fills().size();
            if (measuring) lat.add(dt);
            if ((id & 0xFFFF) == 0 && book.size() > 400_000) book.clear();   // keep the book bounded
        }
        stop[0] = true;
        long[] h = Arrays.copyOf(hiccups, hiccupCount[0]);
        Arrays.sort(h);
        System.out.printf("ops=%d  op_us p50=%.2f p99=%.2f p99.9=%.1f p99.99=%.1f max=%.1f | hiccup_ms p99=%.2f p99.9=%.2f max=%.2f | sink=%d%n",
                lat.count, lat.q(.50) / 1e3, lat.q(.99) / 1e3, lat.q(.999) / 1e3, lat.q(.9999) / 1e3, lat.max / 1e3,
                ms(h, .99), ms(h, .999), h.length == 0 ? 0 : h[h.length - 1] / 1e6, sink);
    }

    /** Log-linear histogram: 64 power-of-two groups x 32 sub-buckets, about 3% resolution. */
    static final class Hist {
        final long[] b = new long[64 * 32]; long count, max;
        void add(long v) { if (v < 1) v = 1; int e = 63 - Long.numberOfLeadingZeros(v); int sub = e < 5 ? (int) v : (int) ((v >> (e - 5)) & 31); b[e * 32 + sub]++; count++; if (v > max) max = v; }
        double q(double q) { long target = (long) Math.ceil(q * count), seen = 0; for (int i = 0; i < b.length; i++) { seen += b[i]; if (seen >= target) { int e = i / 32, sub = i % 32; return e < 5 ? sub : ((32L + sub) << (e - 5)); } } return max; }
    }
    private static double ms(long[] a, double q) { return a.length == 0 ? 0 : a[(int) Math.min(a.length - 1, Math.floor(q * a.length))] / 1e6; }
}
