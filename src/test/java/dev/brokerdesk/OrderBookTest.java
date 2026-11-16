package dev.brokerdesk;

import dev.brokerdesk.domain.Side;
import dev.brokerdesk.engine.OrderBook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTest {
    final OrderBook book = new OrderBook();

    @Test void buyMatchesBestAskAtTheMakersPrice() {
        book.submit(1, 10, Side.SELL, 12_600, 100);
        book.submit(2, 11, Side.SELL, 12_500, 100);
        var m = book.submit(3, 12, Side.BUY, 12_700, 150);
        assertThat(m.fills()).extracting(OrderBook.Fill::makerOrderId, OrderBook.Fill::priceMills, OrderBook.Fill::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, 12_500L, 100L), org.assertj.core.groups.Tuple.tuple(1L, 12_600L, 50L));
        assertThat(m.restingQty()).isZero();
        assertThat(book.depth(5).asks()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(50));
    }

    @Test void timePriorityWithinAPriceLevel() {
        book.submit(1, 10, Side.SELL, 12_500, 100);
        book.submit(2, 11, Side.SELL, 12_500, 100);
        var m = book.submit(3, 12, Side.BUY, 12_500, 100);
        assertThat(m.fills()).singleElement().extracting(OrderBook.Fill::makerOrderId).isEqualTo(1L);
    }

    @Test void nonCrossingOrderRestsAndPartialFillRestsTheRemainder() {
        book.submit(1, 10, Side.SELL, 12_500, 40);
        var m = book.submit(2, 11, Side.BUY, 12_500, 100);
        assertThat(m.fills()).hasSize(1);
        assertThat(m.restingQty()).isEqualTo(60);
        assertThat(book.depth(5).bids()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(60));
    }

    @Test void limitPriceIsRespected() {
        book.submit(1, 10, Side.SELL, 12_600, 100);
        var m = book.submit(2, 11, Side.BUY, 12_500, 100);
        assertThat(m.fills()).isEmpty();
        assertThat(m.restingQty()).isEqualTo(100);
    }

    @Test void selfTradeIsPreventedByCancellingTheIncomingRemainder() {
        book.submit(1, 10, Side.SELL, 12_500, 100);
        var m = book.submit(2, 10, Side.BUY, 12_500, 100);
        assertThat(m.fills()).isEmpty();
        assertThat(m.cancelledQty()).isEqualTo(100);
        assertThat(book.depth(5).bids()).isEmpty();
    }

    @Test void cancelRemovesRestingOrderOnce() {
        book.submit(1, 10, Side.SELL, 12_500, 100);
        assertThat(book.cancel(1)).isPresent();
        assertThat(book.cancel(1)).isEmpty();
        assertThat(book.size()).isZero();
    }

    @Test void bestBidIsTheHighest() {
        book.submit(1, 10, Side.BUY, 12_000, 10);
        book.submit(2, 11, Side.BUY, 12_400, 10);
        assertThat(book.depth(5).bids()).extracting(OrderBook.Level::priceMills).containsExactly(12_400L, 12_000L);
    }
}
