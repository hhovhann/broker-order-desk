package dev.brokerdesk.engine;

import dev.brokerdesk.domain.Side;
import java.util.*;

/**
 * Limit order book for ONE symbol: price priority, then time priority; trades execute at the resting (maker) price.
 * Pure Java, no Spring, no locks: it is owned by a single thread (see MatchingEngine), which is what makes it simple and fast.
 * Self-trade prevention: if the best opposite order belongs to the same account, the incoming remainder is cancelled.
 */
public final class OrderBook {
    public static final class Resting {
        final long orderId, accountId, priceMills; final Side side; long remaining;
        Resting(long orderId, long accountId, Side side, long priceMills, long remaining) {
            this.orderId = orderId; this.accountId = accountId; this.side = side; this.priceMills = priceMills; this.remaining = remaining;
        }
        public long orderId() { return orderId; }
        public long remaining() { return remaining; }
    }

    public record Fill(long makerOrderId, long takerOrderId, long makerAccountId, long takerAccountId, Side takerSide, long priceMills, long quantity) {}
    /** restingQty stays in the book; cancelledQty was removed by self-trade prevention. */
    public record Match(List<Fill> fills, long restingQty, long cancelledQty) {}
    public record Level(long priceMills, long quantity, int orders) {}
    public record Depth(List<Level> bids, List<Level> asks) {}

    private final TreeMap<Long, ArrayDeque<Resting>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Resting>> asks = new TreeMap<>();
    private final Map<Long, Resting> index = new HashMap<>();

    public Match submit(long orderId, long accountId, Side side, long priceMills, long quantity) {
        List<Fill> fills = new ArrayList<>();
        var opposite = side == Side.BUY ? asks : bids;
        long remaining = quantity;
        boolean selfTrade = false;
        while (remaining > 0 && !opposite.isEmpty()) {
            var best = opposite.firstEntry();
            long levelPrice = best.getKey();
            if (side == Side.BUY ? levelPrice > priceMills : levelPrice < priceMills) break;
            Resting maker = best.getValue().peekFirst();
            if (maker.accountId == accountId) { selfTrade = true; break; }
            long qty = Math.min(remaining, maker.remaining);
            fills.add(new Fill(maker.orderId, orderId, maker.accountId, accountId, side, levelPrice, qty));
            maker.remaining -= qty;
            remaining -= qty;
            if (maker.remaining == 0) {
                best.getValue().pollFirst();
                index.remove(maker.orderId);
                if (best.getValue().isEmpty()) opposite.remove(levelPrice);
            }
        }
        if (remaining > 0 && !selfTrade) { rest(orderId, accountId, side, priceMills, remaining); return new Match(fills, remaining, 0); }
        return new Match(fills, 0, remaining);
    }

    /** Puts an order straight into the book (used when rebuilding after a restart). */
    public void rest(long orderId, long accountId, Side side, long priceMills, long remaining) {
        var r = new Resting(orderId, accountId, side, priceMills, remaining);
        (side == Side.BUY ? bids : asks).computeIfAbsent(priceMills, p -> new ArrayDeque<>()).addLast(r);
        index.put(orderId, r);
    }

    /** Removes a resting order. Empty if it is not (or no longer) in the book. */
    public Optional<Resting> cancel(long orderId) {
        Resting r = index.remove(orderId);
        if (r == null) return Optional.empty();
        var side = r.side == Side.BUY ? bids : asks;
        var q = side.get(r.priceMills);
        q.remove(r);
        if (q.isEmpty()) side.remove(r.priceMills);
        return Optional.of(r);
    }

    public void clear() { bids.clear(); asks.clear(); index.clear(); }
    public int size() { return index.size(); }

    public Depth depth(int levels) { return new Depth(levels(bids, levels), levels(asks, levels)); }

    private static List<Level> levels(TreeMap<Long, ArrayDeque<Resting>> side, int max) {
        return side.entrySet().stream().limit(max)
                .map(e -> new Level(e.getKey(), e.getValue().stream().mapToLong(r -> r.remaining).sum(), e.getValue().size())).toList();
    }
}
